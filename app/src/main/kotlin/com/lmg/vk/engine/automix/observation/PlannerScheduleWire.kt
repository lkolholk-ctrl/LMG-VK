package com.lmg.vk.engine.automix.observation

/** Strict bounded representation checks, not a second implementation of styling/rate math. */
internal object PlannerScheduleWire {
    const val ENVELOPE_MAGIC = 0x4c4d47534531L
    const val PLAN_MAGIC = 0x4c4d47535031L
    const val MAX_WORDS = 2048
    // Version1 wire dictionary: exact existing allEffectParameters() order.
    private val parameters = listOf("Ga1g", "Fbw1", "Fcg1", "Fcf1", "HP1f", "HP1r", "LP1f", "LP1r",
        "Ga2g", "DLdt", "DLlf", "DLdw", "DLfb", "RVga", "RVdw", "RVmi", "RVma", "RVlf", "RVhf", "RVrr",
        "HP2f", "HP2r", "LP2f", "LP2r", "Ga3g", "Ga4g", "ts_rate", "out_gain", "bypa")
    private val curves = setOf(0L, 1L, 2L, 64L, 65L, 66L, 128L, 129L)
    private fun ensure(ok: Boolean) {
        if (!ok) throw ObservationFailure(ObservationReason.BRIDGE_CONTRACT_MISMATCH, "SCHEDULE_WIRE")
    }
    fun decode(w: LongArray, request: LongArray, catalogIds: Set<Long>): PlannerSelectionReport {
        ensure(request.size == 24)
        ensure(w.size in 22..MAX_WORDS)
        ensure(w[0] == ENVELOPE_MAGIC && w[1] == 1L && w[2] == w.size.toLong())
        ensure(w[3] == request[3] && w[4] == request[4] && w[5] in 0L..4L && w[8] == 0L && w[9] == 0L)
        ensure(w[6] in 12L..140L && w[7] in 0L..1898L && 10L+w[6]+w[7] == w.size.toLong())
        val sourceEnd = 10+w[6].toInt()
        val source = PlannerSourceContextWire.decode(w.copyOfRange(10, sourceEnd), request, catalogIds)
        val status = PlannerScheduleStatus.entries[w[5].toInt()]
        val resolved = source.sourceContextResolution == PlannerSourceContextResolution.RESOLVED
        ensure((status == PlannerScheduleStatus.SOURCE_REJECTED) == !resolved)
        if (status != PlannerScheduleStatus.COMPILED) {
            ensure(w[7] == 0L)
            when (status) {
                PlannerScheduleStatus.SELECTION_UNAVAILABLE -> ensure(source.candidate == null || !source.completeForResolvedScope)
                PlannerScheduleStatus.UNSUPPORTED_STYLE, PlannerScheduleStatus.INVALID_SCHEDULE ->
                    ensure(source.candidate != null && source.completeForResolvedScope)
                else -> ensure(source.candidate == null)
            }
            return source.withSchedule(status, null)
        }
        ensure(source.completeForResolvedScope && source.candidate != null)
        val c = requireNotNull(source.candidate)
        val p = w.copyOfRange(sourceEnd, w.size)
        ensure(p.size >= 40 && p[0] == PLAN_MAGIC && p[1] == 1L && p[2] == p.size.toLong())
        ensure(p[3] == c.styleId.toLong() && p[4] == c.incomingScale.toLong())
        ensure(p[5] == c.outgoingStartEvent.toLong() && p[6] == c.outgoingEndEvent.toLong() &&
            p[7] == c.incomingStartEvent.toLong() && p[8] == c.incomingEndEvent.toLong())
        ensure(p[9] in 1L..4096L && p[10] in 1L..4096L && p[11] == c.score.toRawBits())
        val coordinates = listOf(c.outgoingStartSeconds,c.outgoingEndSeconds,c.incomingStartSeconds,c.incomingEndSeconds)
        ensure(coordinates.indices.all { p[12+it] == coordinates[it].toRawBits() })
        fun d(i: Int): Double { val v=Double.fromBits(p[i]);ensure(v.isFinite());return v }
        for (i in 11..32) d(i)
        ensure((16..21).all { d(it)>0 } && d(18)==1.0 && d(21)==1.0)
        ensure(p[22]==0L && d(23)>0 && p[25]==0L && p[26]==p[23] && p[28]==p[23])
        ensure(d(27)>=0 && d(27)<d(23) && d(24)>=d(27) && d(24)<=d(23))
        ensure(p[29]==p[12] && p[30]==p[14] && p[31]==p[23] && d(32)>0)
        ensure(p[33] in 1L..32L && p[34] in 1L..32L && p[35] in 4L..256L)
        ensure(p[36]==0L && p[37]==1L && p[38]==0L && p[39]==0L)
        var at=40; var pointTotal=0
        fun automations(n: Int, begin: Double, end: Double): List<ObservedScheduleAutomation> = List(n) {
            ensure(at+2<=p.size)
            val descriptor=p[at++];val size=p[at++]
            ensure(descriptor in 0L..28L && size in 2L..4L && at+3*size<=p.size)
            val id=parameters[descriptor.toInt()]
            var previous=begin
            val points=List(size.toInt()) {
                val value=d(at++);val time=d(at++);val curve=p[at++]
                ensure(time>=begin && time<=end && time>=previous && curve in curves)
                if(id=="ts_rate")ensure(value>0 && curve==128L)
                previous=time
                ObservedSchedulePoint(value,time,curve.toInt())
            }
            for (i in 0 until points.lastIndex) if(points[i].curve==129)
                ensure(points[i].value>0 && points[i+1].value>0)
            pointTotal+=points.size
            ObservedScheduleAutomation(id,points)
        }
        val aa=automations(p[33].toInt(),d(12),d(13))
        val ba=automations(p[34].toInt(),d(14),d(15))
        ensure(at==p.size && pointTotal.toLong()==p[35])
        fun rateCheck(list: List<ObservedScheduleAutomation>, first: Double,last: Double) {
            // The compiler writes these endpoints into every complete ts_rate record.
            for(a in list) if(a.parameterId=="ts_rate")
                ensure(a.points.size==2 && a.points[0].value.toRawBits()==first.toRawBits() &&
                    a.points[1].value.toRawBits()==last.toRawBits())
        }
        rateCheck(aa,d(18),d(19));rateCheck(ba,d(20),d(21))
        val out=ObservedScheduleSide(p[5].toInt(),p[6].toInt(),p[9].toInt(),d(12),d(13),d(25),d(26),d(29),d(31),d(18),d(19),aa)
        val incoming=ObservedScheduleSide(p[7].toInt(),p[8].toInt(),p[10].toInt(),d(14),d(15),d(27),d(28),d(30),d(32),d(20),d(21),ba)
        return source.withSchedule(status,ObservedTransitionSchedule(source.generation,source.bindingRevision,
            c.styleId,c.incomingScale,c.score,d(16),d(17),d(22),d(23),d(24),out,incoming))
    }
}
