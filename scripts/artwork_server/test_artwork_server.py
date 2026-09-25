import importlib.util
import os
import sys
import types
import unittest
from unittest.mock import patch
from artwork_matching import candidate_score, clean_title, normalize

# These tests exercise selection/cache and never launch a browser.
sys.modules.setdefault('playwright', types.ModuleType('playwright'))
sync = types.ModuleType('playwright.sync_api')
sync.sync_playwright = lambda: None
sys.modules.setdefault('playwright.sync_api', sync)
spec = importlib.util.spec_from_file_location('proxy', os.environ['ARTWORK_PROXY_SOURCE'])
proxy = importlib.util.module_from_spec(spec)
spec.loader.exec_module(proxy)

class MatchingTests(unittest.TestCase):
    def test_actual_vk_bruised_sky_mixed_alphabet(self):
        self.assertEqual('Bruised Sky', clean_title('Bruised Sk\u0443'))
        c=self.candidate(dur=220.837)
        self.assertIsNotNone(candidate_score(c,'Bruised Sk\u0443','Poppy',220.75,'single'))
        self.assertIsNone(candidate_score(c,'Bruised Sk\u0443 (Live)','Poppy',220.75))
        self.assertEqual('рок море кино русскиеrock',normalize('рок море кино русскиеRock'))

    def test_search_uses_repaired_title_and_avoids_old_miss_key(self):
        keys=[]
        with patch.object(proxy,'get_cached_motion',side_effect=lambda k: keys.append(k)), patch.object(proxy,'set_cached_motion'), patch.object(proxy,'search_catalog',return_value=[]) as search:
            proxy.resolve_motion_artwork({'title':['Bruised Sk\u0443'],'artist':['Poppy'],'duration':['220.75'],'include_mp4':['0']},None)
        self.assertEqual('Bruised Sky',search.call_args.args[0])
        self.assertNotIn('Sk\u0443',keys[0])

    def candidate(self, **kw):
        return dict({'id':'1', 'name':'Bruised Sky', 'artist':'Poppy', 'album':'Empty Hands', 'dur':200}, **kw)

    def test_wrong_identity_and_versions_rejected(self):
        for c in (self.candidate(artist='Other'), self.candidate(name='Bruised Sky Live'), self.candidate(dur=220)):
            self.assertIsNone(candidate_score(c, 'Bruised Sky','Poppy',200))

    def test_features_and_diacritics_match(self):
        self.assertIsNotNone(candidate_score(self.candidate(name='Fade (feat. Inéz)', artist='Sub Focus'), 'fade', 'inez, sub focus', 200))

    def test_cleanup_preserves_real_words(self):
        for title in ('House', 'Electronic', 'Song (Live)', 'Bass'):
            self.assertEqual(title, clean_title(title+' [320 kbps]'))
        self.assertEqual('Song',clean_title('Song [vk.com/test] Electronic [320 kbps]'))

    def test_unknown_duration(self):
        self.assertIsNotNone(candidate_score(self.candidate(),'Bruised Sky','Poppy',None))

    def test_sao_paulo_capitals_and_accents(self):
        c=self.candidate(name='São Paulo',artist='The Weeknd & Anitta',dur=301.623)
        for title in ('SÃO PAULO','SAO PAULO','sao paulo'):
            self.assertIsNotNone(candidate_score(c,title,'The Weeknd',301))

    def test_hls_client_never_waits_for_mp4_extraction(self):
        attrs={'name':'Album','editorialVideo':{'motionDetailTall':{'video':'https://example.com/video.m3u8'}}}
        with patch.object(proxy,'extract_mp4_from_m3u8',side_effect=AssertionError('unnecessary network')):
            result=proxy._build_motion_result('1',attrs,include_mp4=False)
        self.assertTrue(result['has_motion'])
        self.assertEqual('https://example.com/video.m3u8',result['tall']['m3u8'])
        self.assertIsNone(result['tall']['mp4'])

    def test_cached_motion_never_needs_browser(self):
        cached={'has_motion':True,'title':'Bruised Sky','artist':'Poppy'}
        with patch.object(proxy,'get_cached_motion',return_value=cached), patch.object(proxy,'refresh_headers',side_effect=AssertionError('browser')):
            self.assertEqual(cached,proxy.resolve_motion_artwork({'title':['Bruised Sky'],'artist':['Poppy']},None))

    def test_duration_part_of_cache_identity(self):
        keys=[]
        with patch.object(proxy,'get_cached_motion',side_effect=lambda k: keys.append(k) or {}):
            for dur in ('200','230'):
                proxy.resolve_motion_artwork({'title':['Bruised Sky'],'artist':['Poppy'],'duration':[dur]},None)
        self.assertNotEqual(*keys)

    def test_selects_correct_candidate_even_when_wrong_one_has_motion(self):
        calls=[]
        def fetch(path,hdrs):
            calls.append(path)
            return {'data':[{'id':'2','attributes':{'name':'Bruised Sky','artistName':'Poppy','durationInMillis':200000,'artwork':{'url':'https://example.com/{w}x{h}.jpg'}}}]}
        with patch.object(proxy,'get_cached_motion',return_value=None), patch.object(proxy,'set_cached_motion'), patch.object(proxy,'api_get',side_effect=fetch), patch.object(proxy,'search_catalog',return_value=[self.candidate(id='1',artist='Other'),self.candidate(id='2')]):
            result=proxy.resolve_motion_artwork({'title':['Bruised Sky'],'artist':['Poppy'],'duration':['200']},None)
        self.assertEqual('2',result['track_id'])
        self.assertEqual(200000,result['duration_ms'])
        self.assertEqual(1,len(calls))
        self.assertIn('/songs/2?',calls[0])

    def test_all_transport_failures_raise_instead_of_cached_miss(self):
        with patch.object(proxy,'api_get',side_effect=OSError('offline')), patch.object(proxy.urllib.request,'urlopen',side_effect=OSError('offline')):
            with self.assertRaises(OSError):
                proxy.search_catalog('Bruised Sky','Poppy',None,strict_errors=True)

    def test_empty_public_fallback_does_not_hide_catalog_outage(self):
        with patch.object(proxy,'api_get',side_effect=OSError('offline')), patch.object(proxy.json,'load',return_value={'results':[]}), patch.object(proxy.urllib.request,'urlopen'):
            with self.assertRaises(RuntimeError):
                proxy.search_catalog('Bruised Sky','Poppy',None,strict_errors=True)

if __name__=='__main__':
    unittest.main()
