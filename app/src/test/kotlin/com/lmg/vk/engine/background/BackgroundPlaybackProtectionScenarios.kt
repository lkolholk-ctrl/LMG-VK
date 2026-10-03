package com.lmg.vk.engine.background

private var groups = 0
private fun test(name: String, body: () -> Unit) { body(); groups++; println("PASS $name") }
private fun yes(x: Boolean, name: String = "assertion") { check(x) { name } }
private fun eq(x: Any?, y: Any?) { check(x == y) { "$x != $y" } }
private fun sample(now: Long, bytes: Long = 1000, last: Long = 1000, pos: Long = 10_000_000,
    epoch: Long = 1, eligible: Boolean = true, media: Long = 10_000) = PlaybackHealthInput(
    now, epoch, 1, media, 549_000, 32_000, eligible,
    AudioOutputProgress(1, 2, true, 1f, bytes, last, pos, false, true))
private fun armed(policy: PlaybackProtectionPolicy): PlaybackRecoveryTicket {
    eq(policy.observe(sample(1000)), null)
    eq(policy.observe(sample(7000)), null) // Still settling after start/seek.
    return checkNotNull(policy.observe(sample(9000)))
}

private class Host : PlaybackRecoveryHost {
    var state = sample(1000)
    val playback = ArrayDeque<() -> Unit>()
    val main = ArrayDeque<() -> Unit>()
    val restarts = ArrayList<Pair<Int, Long>>()
    val events = ArrayList<String>()
    var owner = "main"
    var failing = false
    override fun input(): PlaybackHealthInput { eq(owner, "main"); return state }
    override fun epoch() = state.epoch
    override fun elapsedMs() = state.nowMs
    override fun output() = state.output
    override fun postPlayback(block: () -> Unit) { playback.add(block) }
    override fun postApplication(block: () -> Unit) { main.add(block) }
    override fun reprepareSamePlayer(itemIndex: Int, positionMs: Long) {
        eq(owner, "main")
        if (failing) throw IllegalStateException("test")
        restarts.add(itemIndex to positionMs)
    }
    override fun event(message: String) { events.add(message) }
    fun onPlayback() { owner = "playback"; playback.removeFirst().invoke(); owner = "main" }
    fun onMain() { main.removeFirst().invoke() }
}
private fun arm(h: Host, c: PlaybackRecoveryController) {
    c.tick(); h.state = sample(9000); c.tick()
    eq(h.playback.size, 1); eq(h.restarts.size, 0)
}

fun runBackgroundPlaybackProtectionScenarios() {
    groups = 0
    test("wake follows intent during buffering, no periodic expiry") {
        repeat(1200) { yes(PlaybackProtectionPolicy.needsCpu(true,true,false,false)) }
    }
    test("no lock for paused buffering") { yes(!PlaybackProtectionPolicy.needsCpu(false,true,false,false)) }
    test("no lock for stopped/ended") { yes(!PlaybackProtectionPolicy.needsCpu(true,false,false,false)) }
    test("no lock when suppressed") { yes(!PlaybackProtectionPolicy.needsCpu(true,true,true,false)) }
    test("close releases lock requirement") { yes(!PlaybackProtectionPolicy.needsCpu(true,true,false,true)) }
    test("foreground repairs bounded and honor user intent") {
        val p=PlaybackProtectionPolicy()
        yes(p.foregroundRepairDue(0,true,false)); yes(!p.foregroundRepairDue(1,true,false))
        yes(p.foregroundRepairDue(5000,true,false)); yes(p.foregroundRepairDue(10000,true,false))
        yes(!p.foregroundRepairDue(15000,true,false)); yes(!p.foregroundRepairDue(999999,true,false))
        yes(!p.foregroundRepairDue(1000000,false,false)); yes(p.foregroundRepairDue(1000001,true,false))
    }
    test("foreground success resets repair budget") {
        val p=PlaybackProtectionPolicy(); yes(p.foregroundRepairDue(0,true,false))
        yes(!p.foregroundRepairDue(1,true,true)); yes(p.foregroundRepairDue(2,true,false))
    }
    test("never restart from one stale snapshot") { eq(PlaybackProtectionPolicy().observe(sample(99_000)),null) }
    test("sustained stationary bytes and sink clock trigger") {
        val p=PlaybackProtectionPolicy();val t=armed(p);yes(p.confirm(t,sample(9001)))
    }
    test("short in-flight write does not erase the stable observation window") {
        val p=PlaybackProtectionPolicy();p.observe(sample(1000))
        eq(p.observe(sample(4000).let { it.copy(output=it.output!!.copy(inFlight=true)) }),null)
        eq(p.observe(sample(4500)),null)
        yes(p.observe(sample(9000)) != null)
    }
    test("in-flight write still forbids confirming a restart") {
        val p=PlaybackProtectionPolicy();val t=armed(p)
        yes(!p.confirm(t,sample(9001).let { it.copy(output=it.output!!.copy(inFlight=true)) }))
    }
    test("delayed poll uses actual output time instead of treating old progress as fresh") {
        val p=PlaybackProtectionPolicy()
        for(now in 1000L..10000L step 500L) {
            val s=sample(now,bytes=now,last=now,pos=now*1000,media=now)
            eq(p.observe(s.copy(output=s.output!!.copy(lastPositionAdvanceMs=now))),null)
        }
        // Work continued for another four seconds, then stopped while Main was delayed.
        val s=sample(20200,bytes=14000,last=14000,pos=14_000_000,media=14000)
        val t=checkNotNull(p.observe(s.copy(output=s.output!!.copy(lastPositionAdvanceMs=14000))))
        yes(p.confirm(t,s.copy(nowMs=20201,output=s.output.copy(lastPositionAdvanceMs=14000))))
    }
    test("fresh hardware position prevents recovery even if accepted bytes are old") {
        val p=PlaybackProtectionPolicy();p.observe(sample(1000))
        val s=sample(20000,pos=12000000)
        eq(p.observe(s.copy(output=s.output!!.copy(lastPositionAdvanceMs=19999))),null)
    }
    test("signal amplitude is not an input") {
        val p=PlaybackProtectionPolicy()
        for(i in 0L..100) eq(p.observe(sample(1000+i*1000,1000+i*176400,1000+i*1000,
            10_000_000+i*1_000_000,media=10_000+i*1000)),null)
    }
    test("flowing bytes but stable displayed clock never restart") {
        val p=PlaybackProtectionPolicy()
        for(i in 0L..100) eq(p.observe(sample(1000+i*1000,1000+i*176400,1000+i*1000)),null)
    }
    test("queued audio draining never restart") {
        val p=PlaybackProtectionPolicy()
        for(i in 0L..100) eq(p.observe(sample(1000+i*1000,pos=10_000_000+i*1_000_000)),null)
    }
    test("background gap followed by progress cancels recovery") {
        val p=PlaybackProtectionPolicy();p.observe(sample(1000))
        eq(p.observe(sample(18318,bytes=816616,last=6000,pos=14_619_000,media=14619)),null)
        eq(p.observe(sample(20318,bytes=1000000,last=20300,pos=16_619_000,media=16619)),null)
    }
    val blocked = listOf<(PlaybackHealthInput)->PlaybackHealthInput>(
        {it.copy(eligible=false)}, {it.copy(bufferedMs=4999)}, {it.copy(itemIndex=-1)},
        {it.copy(positionMs=-1)}, {it.copy(durationMs=1000)}, {it.copy(output=null)},
        {it.copy(output=it.output!!.copy(inFlight=true))}, {it.copy(output=it.output!!.copy(valid=false))},
        {it.copy(output=it.output!!.copy(playing=false))}, {it.copy(output=it.output!!.copy(gain=0f))},
        {it.copy(output=it.output!!.copy(gain=Float.NaN))}, {it.copy(output=it.output!!.copy(gain=Float.POSITIVE_INFINITY))},
        {it.copy(output=it.output!!.copy(acceptedBytes=0))}, {it.copy(output=it.output!!.copy(lastAcceptedMs=-1))},
        {it.copy(output=it.output!!.copy(positionUs=Long.MIN_VALUE))}, {it.copy(output=it.output!!.copy(lastAcceptedMs=100000))},
    )
    blocked.forEachIndexed { index, f -> test("unsafe predicate $index never restarts") {
        val p=PlaybackProtectionPolicy(); for(t in listOf(1000L,9000L,20000L)) eq(p.observe(f(sample(t))),null)
    } }
    test("invalid snapshot cancels previous evidence") {
        val p=PlaybackProtectionPolicy();p.observe(sample(1000));p.observe(sample(8999).copy(output=null))
        eq(p.observe(sample(9000)),null)
    }
    test("source epoch change cancels") {
        val p=PlaybackProtectionPolicy();val t=armed(p);yes(!p.confirm(t,sample(9001,epoch=2)))
    }
    test("sink replacement cancels") {
        val p=PlaybackProtectionPolicy();val t=armed(p)
        yes(!p.confirm(t,sample(9001).let{it.copy(output=it.output!!.copy(epoch=4))}))
    }
    test("queue index change cancels") {
        val p=PlaybackProtectionPolicy();val t=armed(p);yes(!p.confirm(t,sample(9001).copy(itemIndex=2)))
    }
    test("post-claim data progress cancels") {
        val p=PlaybackProtectionPolicy();val t=armed(p);yes(!p.confirm(t,sample(9001,bytes=2000,last=9001)))
    }
    test("sink clock progress cancels") {
        val p=PlaybackProtectionPolicy();val t=armed(p);yes(!p.confirm(t,sample(9001,pos=10000001)))
    }
    test("late acknowledgement cancels") {
        val p=PlaybackProtectionPolicy();val t=armed(p);yes(!p.confirm(t,sample(11001)))
    }
    test("foreign ticket rejected") {
        val p=PlaybackProtectionPolicy();val t=armed(p)
        val copy=PlaybackRecoveryTicket(t.epoch,t.itemIndex,t.sinkId,t.sinkEpoch,t.acceptedBytes,t.positionUs,t.gain,t.durationMs,t.resumePositionMs,t.createdMs)
        yes(!p.confirm(copy,sample(9001)));yes(p.confirm(t,sample(9001)))
    }
    test("ticket is single use") {
        val p=PlaybackProtectionPolicy();val t=armed(p);yes(p.confirm(t,sample(9001)));yes(!p.confirm(t,sample(9002)))
    }
    test("resume uses real sink delta not moving notification time") {
        val p=PlaybackProtectionPolicy();p.observe(sample(1000))
        p.observe(sample(2000,bytes=2000,last=2000,pos=10500000,media=11000))
        val t=checkNotNull(p.observe(sample(10000,bytes=2000,last=2000,pos=10500000,media=19000)))
        eq(t.resumePositionMs,10500L)
    }
    test("counter rollback restarts settling, not player") {
        val p=PlaybackProtectionPolicy();p.observe(sample(1000))
        eq(p.observe(sample(9000,bytes=999)),null)
    }
    test("gain changes reset settling") {
        val p=PlaybackProtectionPolicy();p.observe(sample(1000))
        eq(p.observe(sample(9000).let{it.copy(output=it.output!!.copy(gain=.2f))}),null)
    }
    test("gain changing after request cancels confirmation") {
        val p=PlaybackProtectionPolicy();val t=armed(p)
        yes(!p.confirm(t,sample(9001).let{it.copy(output=it.output!!.copy(gain=.5f))}))
    }
    test("duration changing after request cancels confirmation") {
        val p=PlaybackProtectionPolicy();val t=armed(p)
        yes(!p.confirm(t,sample(9001).copy(durationMs=550000)))
    }
    test("fractional sink progress is retained not rounded per poll") {
        val p=PlaybackProtectionPolicy(); p.observe(sample(1000))
        for(i in 1L..20L) p.observe(sample(1000+i*500,bytes=1000+i,last=1000+i*500,
            pos=10000000+i*500100,media=10000+i*501))
        val t=checkNotNull(p.observe(sample(18000,bytes=1020,last=11000,pos=20002000,media=27000)))
        eq(t.resumePositionMs,20002L)
    }
    test("budget survives seeks and pauses") {
        val p=PlaybackProtectionPolicy();val first=armed(p);yes(p.confirm(first,sample(9001)))
        p.invalidate();p.observe(sample(70000,epoch=2));val second=checkNotNull(p.observe(sample(78000,epoch=2)))
        yes(p.confirm(second,sample(78001,epoch=2)))
        p.invalidate();p.observe(sample(140000,epoch=3));eq(p.observe(sample(148000,epoch=3)),null)
        p.invalidate();p.observe(sample(700000,epoch=4));yes(p.observe(sample(708000,epoch=4))!=null)
    }
    test("invalidation cannot flood blocked playback thread") {
        val p=PlaybackProtectionPolicy();armed(p);p.invalidate()
        p.observe(sample(12000,epoch=2));eq(p.observe(sample(20000,epoch=2)),null)
    }
    test("closed policy cannot recover or renew foreground") {
        val p=PlaybackProtectionPolicy();p.close();eq(p.observe(sample(1)),null)
        yes(!p.foregroundRepairDue(1,true,false))
    }
    test("real controller requires BOTH looper confirmations") {
        val h=Host();val c=PlaybackRecoveryController(h);arm(h,c)
        h.onPlayback();eq(h.restarts.size,0);h.onMain();eq(h.restarts,listOf(1 to 10000L))
    }
    test("pause before playback acknowledgement cancels") {
        val h=Host();val c=PlaybackRecoveryController(h);arm(h,c)
        h.state=h.state.copy(epoch=2,eligible=false);c.invalidate();h.onPlayback();eq(h.main.size,0);eq(h.restarts.size,0)
    }
    test("pause after acknowledgement cancels") {
        val h=Host();val c=PlaybackRecoveryController(h);arm(h,c);h.onPlayback()
        h.state=h.state.copy(epoch=2,eligible=false);c.invalidate();h.onMain();eq(h.restarts.size,0)
    }
    test("write resumes before acknowledgement cancels") {
        val h=Host();val c=PlaybackRecoveryController(h);arm(h,c)
        h.state=sample(9001,bytes=2000,last=9001);h.onPlayback();eq(h.main.size,0)
    }
    test("write resumes after acknowledgement cancels") {
        val h=Host();val c=PlaybackRecoveryController(h);arm(h,c);h.onPlayback()
        h.state=sample(9001,bytes=2000,last=9001);h.onMain();eq(h.restarts.size,0)
    }
    test("blocked acknowledgement expires, no queued restart") {
        val h=Host();val c=PlaybackRecoveryController(h);arm(h,c)
        h.state=sample(30000);h.onPlayback();eq(h.main.size,0)
    }
    test("close before acknowledgement never restarts") {
        val h=Host();val c=PlaybackRecoveryController(h);arm(h,c);c.close();h.onPlayback();eq(h.main.size,0)
    }
    test("close after acknowledgement never restarts") {
        val h=Host();val c=PlaybackRecoveryController(h);arm(h,c);h.onPlayback();c.close();h.onMain();eq(h.restarts.size,0)
    }
    test("native takeover before restart suppresses it") {
        val h=Host();val c=PlaybackRecoveryController(h);arm(h,c);h.onPlayback()
        h.state=h.state.copy(eligible=false);h.onMain();eq(h.restarts.size,0)
    }
    test("exception is logged, not an autoplay/retry loop") {
        val h=Host();val c=PlaybackRecoveryController(h);arm(h,c);h.failing=true;h.onPlayback();h.onMain()
        eq(h.restarts.size,0);yes(h.events.any{it.contains("reprepare-failed")})
        repeat(50){h.state=h.state.copy(nowMs=10000L+it*500);c.tick()};eq(h.playback.size,0)
    }
    println("BACKGROUND_PROTECTION_HOST_PASSED groups=$groups")
}

fun main() { runBackgroundPlaybackProtectionScenarios() }
