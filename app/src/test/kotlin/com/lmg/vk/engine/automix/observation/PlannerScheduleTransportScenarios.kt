package com.lmg.vk.engine.automix.observation

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Positives are produced by real C++ composition. Corruptions are rejected by Kotlin. */
object PlannerScheduleTransportScenarios {
    @JvmStatic fun main(args: Array<String>) {
        val input=ByteBuffer.wrap(File(args.single()).readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        val count=input.long.toInt();check(count==16)
        val ids=setOf(8L,9L,12L);var rejected=0
        fun rejects(w: LongArray,q: LongArray) {
            var failed=false
            try { PlannerScheduleWire.decode(w,q,ids) }
            catch(e: ObservationFailure) { check(e.reason==ObservationReason.BRIDGE_CONTRACT_MISMATCH);failed=true }
            check(failed);rejected++
        }
        repeat(count) { fixture ->
            check(input.long==24L);val q=LongArray(24){input.long}
            val n=input.long.toInt();check(n in 22..2048);val w=LongArray(n){input.long}
            val r=PlannerScheduleWire.decode(w,q,ids)
            check(!r.canExecute && r.generation==q[3] && r.bindingRevision==q[4])
            check(r.scheduleStatus?.ordinal?.toLong()==w[5])
            if(fixture==0) {
                check(q.contentEquals(PlannerSourceContextWire.request(71,1,PlannerSourceTransportScenarios.context(),120000,120000)))
                val p=requireNotNull(r.schedule);check(p.styleId==9 && p.score==15.104 && p.transitionEndSeconds==16.0)
                check(p.outgoing.sourceStartSeconds==104.0 && p.incoming.sourceStartSeconds==0.0 && p.referenceTransitionTimeSeconds==8.0)
                check(p.outgoing.automations.map{it.parameterId}==listOf("bypa","ts_rate","out_gain","LP1f"))
                check(p.outgoing.automations[0].points.map{it.songTimeSeconds}==listOf(104.0,104.0,120.0,120.0))
                check(p.outgoing.automations[3].points[0].value==22000.0) // Do NOT clamp to descriptor maximum.
            }
            if(fixture==1)check(r.schedule?.styleId==12)
            if(w[5]==0L) {
                val plan=requireNotNull(r.schedule);check(!plan.canExecute && plan.requiresPositionRevalidation)
                var immutable=0
                try {(plan.outgoing.automations as MutableList).clear()}catch(_:UnsupportedOperationException){immutable++}
                try {(plan.outgoing.automations[0].points as MutableList)[0]=ObservedSchedulePoint(9.0,9.0,0)}catch(_:UnsupportedOperationException){immutable++}
                check(immutable==2)
                val start=10+w[6].toInt()
                val changes=mapOf(0 to 0L,1 to 2L,2 to 0L,3 to 33L,4 to 3L,5 to -1L,6 to 4096L,
                    7 to -1L,8 to 4096L,9 to 0L,10 to 4097L,11 to 0L,12 to (-1.0).toRawBits(),
                    16 to 0L,17 to 0L,18 to 0L,19 to 0L,20 to 0L,21 to 0L,22 to 1L,23 to 0L,
                    24 to Double.POSITIVE_INFINITY.toRawBits(),25 to 1L,26 to 0L,27 to (-1.0).toRawBits(),
                    28 to 0L,29 to (-1.0).toRawBits(),30 to (-1.0).toRawBits(),31 to 0L,32 to 0L,
                    33 to 33L,34 to 0L,35 to 257L,36 to 1L,37 to 2L,38 to 1L,39 to 1L)
                changes.forEach{(i,v)->rejects(w.copyOf().also{it[start+i]=v},q)}
                for(i in 11..32)rejects(w.copyOf().also{it[start+i]=Double.NaN.toRawBits()},q)
                var at=start+40
                repeat((w[start+33]+w[start+34]).toInt()) {
                    val size=w[at+1].toInt()
                    rejects(w.copyOf().also{it[at]=29},q);rejects(w.copyOf().also{it[at+1]=5},q)
                    repeat(size){k->
                        rejects(w.copyOf().also{it[at+2+k*3]=Double.NaN.toRawBits()},q)
                        rejects(w.copyOf().also{it[at+3+k*3]=(-1.0).toRawBits()},q)
                        rejects(w.copyOf().also{it[at+4+k*3]=252},q)
                    };at+=2+size*3
                }
            } else check(r.schedule==null)
            for(i in listOf(0,1,2,3,4,5,6,7,8,9)) {
                val replacement=when(i){0->0L;1->2L;2->-1L;3->q[3]+1;4->q[4]+1;5->5L;6->141L;7->2048L;else->1L}
                rejects(w.copyOf().also{it[i]=replacement},q)
            }
            for(size in listOf(0,10,21,w.lastIndex))rejects(w.copyOf(size),q)
            rejects(w.copyOf(w.size+1),q)
            rejects(w,q.copyOf().also{it[3]++});rejects(w,q.copyOf().also{it[4]++})
            // Decoder owns values: callers may mutate native transport afterwards.
            val old=r.schedule?.outgoing?.automations?.firstOrNull()?.points?.firstOrNull()?.value
            w.fill(0);if(old!=null)check(requireNotNull(r.schedule).outgoing.automations.first().points.first().value==old)
        }
        check(!input.hasRemaining())
        println("Schedule transport: $count native snapshots accepted; $rejected malformed variants rejected; immutable records and request match verified")
    }
}
