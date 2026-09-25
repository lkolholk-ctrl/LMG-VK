"""Catalog identity matching shared by the motion endpoint's candidate selection."""
import re
import unicodedata

_LOOKALIKES = dict(zip('аеорсухіјѕ', 'aeopcyxijs'))
_LOOKALIKES.update({k.upper(): v.upper() for k, v in list(_LOOKALIKES.items())})


def repair_mixed_words(value):
    def repair(match):
        word = match.group()
        if re.search('[A-Za-z]', word) and all(c.isascii() or c in _LOOKALIKES for c in word):
            return ''.join(_LOOKALIKES.get(c, c) for c in word)
        return word
    return re.sub(r'[^\W_]+', repair, value)


def normalize(value):
    value = ''.join(c for c in unicodedata.normalize('NFKD', value)
                    if not unicodedata.combining(c)).lower().replace('ё', 'е')
    return ' '.join(re.sub(r'[^\w]+|_', ' ', repair_mixed_words(value)).split())


def clean_title(value):
    noise = r'[\[(]\s*(?:(?:https?://)?(?:www\.)?(?:vk\.com|vk\.ru|vkontakte\.ru)/[^\s\])]+|\d{2,4}\s*(?:kbps|kb/s|кбит/с))\s*[\])]'
    value = re.sub(noise + r'\s*[-–—|:]?\s*(?:electronic|electronica|hip[ -]?hop|rap|pop|rock|dance|house|techno|trance|dubstep|metal|r&b)(?:\s*' + noise + r')*\s*$', ' ', value, flags=re.I)
    return repair_mixed_words(' '.join(re.sub(noise, ' ', value, flags=re.I).split()))


def artists(value):
    value = re.sub(r'\b(?:feat\.?|ft\.?|featuring)\s+|п\.?\s*у\.?\s+', ' & ', value, flags=re.I)
    return {normalize(x) for x in re.split(r'\s*[&,;]\s*|\s+[x×]\s+', value, flags=re.I) if normalize(x)}


def signature(title, artist):
    title = clean_title(title)
    featured = re.search(r'\s+(?:\(|\[)?(?:feat\.?|ft\.?|featuring)\s+(.+?)(?:\)|\])?\s*$', title, re.I)
    members = artists(clean_title(artist))
    if featured:
        members |= artists(featured.group(1))
        title = title[:featured.start()]
    return normalize(title), members


def candidate_score(candidate, title, artist, duration=None, album=''):
    expected, members = signature(title, artist)
    actual, other = signature(candidate['name'], candidate['artist'])
    if not expected or actual != expected or not members or not other:
        return None
    if not (members <= other or other <= members):
        return None
    actual_duration = candidate.get('dur') or 0
    difference = abs(actual_duration - duration) if duration and actual_duration else 0
    if difference > 6:
        return None
    return (members != other, bool(album) and normalize(album) != normalize(candidate.get('album', '')), difference)
