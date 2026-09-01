package com.garagelog.app.util

import com.garagelog.app.data.entity.BuildPhaseEntity
import com.garagelog.app.data.entity.BuildStepEntity
import com.garagelog.app.data.entity.PhaseStatus
import com.garagelog.app.data.entity.StepPriority

/** The cost a step actually counts against a phase's budget — actual once done, else estimated. */
private fun BuildStepEntity.effectiveCost(): Double =
    (if (status == PhaseStatus.Done.label) actualCost ?: estimatedCost else estimatedCost) ?: 0.0

/**
 * Auto-assigns every non-deleted, non-[BuildStepEntity.manualPhaseOverride] step for one vehicle
 * to a phase, greedily: steps are walked highest-[StepPriority] first (ties broken by [BuildStepEntity.order]),
 * and for each step we take the first phase (in [BuildPhaseEntity.order]) whose [BuildPhaseEntity.priorityFilter]
 * accepts the step's priority (null = accepts any) and whose [BuildPhaseEntity.budgetCap] still has room
 * (null = unlimited) once that phase's already-assigned steps are counted. A step that fits nowhere is
 * left unbucketed (phaseId = null) for the user to place by hand.
 *
 * Pure function — callers diff the result against current assignments to detect promotions worth a toast.
 */
fun assignBuildBuckets(steps: List<BuildStepEntity>, phases: List<BuildPhaseEntity>): Map<String, String?> {
    val orderedPhases = phases.sortedBy { it.order }
    val manuallyPinned = steps.filter { it.manualPhaseOverride }
    val autoAssignable = steps.filterNot { it.manualPhaseOverride }
        .sortedWith(
            compareByDescending<BuildStepEntity> { StepPriority.fromLabel(it.priority).ordinal }
                .thenBy { it.order },
        )

    val spentByPhase = mutableMapOf<String, Double>()
    manuallyPinned.forEach { step ->
        step.phaseId?.let { spentByPhase[it] = (spentByPhase[it] ?: 0.0) + step.effectiveCost() }
    }

    val result = mutableMapOf<String, String?>()
    for (step in autoAssignable) {
        val cost = step.effectiveCost()
        val phase = orderedPhases.firstOrNull { candidate ->
            val priorityOk = candidate.priorityFilter == null || candidate.priorityFilter == step.priority
            val spent = spentByPhase[candidate.id] ?: 0.0
            val budgetOk = candidate.budgetCap == null || spent + cost <= candidate.budgetCap
            priorityOk && budgetOk
        }
        result[step.id] = phase?.id
        if (phase != null) spentByPhase[phase.id] = (spentByPhase[phase.id] ?: 0.0) + cost
    }
    return result
}
