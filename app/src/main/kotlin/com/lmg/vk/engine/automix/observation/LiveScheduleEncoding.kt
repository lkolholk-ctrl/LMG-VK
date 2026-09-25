package com.lmg.vk.engine.automix.observation

import com.lmg.vk.engine.automix.render.RenderExecutionData

/** Structural encoding of the already validated Stage4a object, not rate/style math. */
internal fun encodeLiveSchedule(s: ObservedTransitionSchedule): RenderExecutionData {
    val ids = listOf("Ga1g","Fbw1","Fcg1","Fcf1","HP1f","HP1r","LP1f","LP1r","Ga2g","DLdt","DLlf","DLdw","DLfb",
        "RVga","RVdw","RVmi","RVma","RVlf","RVhf","RVrr","HP2f","HP2r","LP2f","LP2r","Ga3g","Ga4g","ts_rate","out_gain","bypa")
    val w=MutableList(40){0L};val a=s.outgoing;val b=s.incoming
    w[0]=0x4c4d47535031L;w[1]=1;w[3]=s.styleId.toLong();w[4]=s.incomingScale.toLong()
    w[5]=a.startEvent.toLong();w[6]=a.endEvent.toLong();w[7]=b.startEvent.toLong();w[8]=b.endEvent.toLong()
    w[9]=a.beatCount.toLong();w[10]=b.beatCount.toLong();w[11]=s.score.toRawBits()
    val values=doubleArrayOf(a.sourceStartSeconds,a.sourceEndSeconds,b.sourceStartSeconds,b.sourceEndSeconds,
        s.scaledBeatRatio,s.effectiveIncomingDurationSeconds,a.startRate,a.endRate,b.startRate,b.endRate,
        s.transitionStartSeconds,s.transitionEndSeconds,s.referenceTransitionTimeSeconds,
        a.playbackTransitionStartSeconds,a.playbackTransitionEndSeconds,b.playbackTransitionStartSeconds,b.playbackTransitionEndSeconds,
        a.startPlaybackSongTimeSeconds,b.startPlaybackSongTimeSeconds,a.stretchedRegionDurationSeconds,b.stretchedRegionDurationSeconds)
    values.forEachIndexed{i,v->w[12+i]=v.toRawBits()}
    w[33]=a.automations.size.toLong();w[34]=b.automations.size.toLong();w[37]=1
    var points=0L
    for(side in listOf(a,b))for(lane in side.automations){
        val id=ids.indexOf(lane.parameterId);check(id>=0);w+=id.toLong();w+=lane.points.size.toLong()
        for(p in lane.points){w+=p.value.toRawBits();w+=p.songTimeSeconds.toRawBits();w+=p.curve.toLong();++points}
    }
    w[35]=points;w[2]=w.size.toLong()
    return RenderExecutionData(w.toLongArray())
}
