package com.jarvis.assistant.automation.agent

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

enum class TaskStepStatus {
    PENDING,
    RUNNING,
    VERIFYING,
    WAITING_FOR_UI,
    RECOVERING,
    COMPLETED,
    FAILED,
    BLOCKED
}

data class AgentTaskStep(
    val stepId: String,
    val title: String,
    val status: TaskStepStatus
)

data class AgentTaskSnapshot(
    val taskId: String,
    val originalGoal: String,
    val progressPercentage: Int,
    val currentStepId: String?,
    val currentStatus: String,
    val steps: List<AgentTaskStep>
)

object AgentTaskEngine {
    private val _taskState = MutableStateFlow<AgentTaskSnapshot?>(null)
    val taskState: StateFlow<AgentTaskSnapshot?> = _taskState.asStateFlow()
}

object AgentTaskHistoryRepository {

    enum class StepStatus(val label: String) {
        COMPLETED("Completed"),
        RUNNING("Running"),
        WAITING_FOR_APPROVAL("Approval Needed"),
        FAILED("Failed"),
        SKIPPED("Skipped"),
        PENDING("Pending")
    }

    data class PlanStepRecord(
        val stepIndex: Int,
        val totalSteps: Int,
        val title: String,
        val targetApp: String,
        val status: StepStatus,
        val verificationNote: String = "",
        val approvalPrompt: String? = null
    ) {
        fun formattedDashboardLine(): String =
            "Task $stepIndex/$totalSteps: $title ($targetApp) — ${status.label}"
    }

    data class MultiTaskPlanRecord(
        val planId: String,
        val originalVoiceCommand: String,
        val overallStatus: String,
        val steps: List<PlanStepRecord>
    )

    fun init(context: Context) {
        // Initialized for session task tracking
    }
}

object MasterAutonomousAgent {

    enum class HighRiskActionType(
        val badgeLabel: String,
        val badgeColorHex: String
    ) {
        SOCIAL_MEDIA_POST("HIGH RISK • SOCIAL POST CONFIRMATION", "#D97706"),
        MESSAGE_OR_POST_DELETION("HIGH RISK • DELETION CONFIRMATION", "#DC2626"),
        SEND_ATTACHMENT("HIGH RISK • ATTACHMENT CONFIRMATION", "#2563EB")
    }

    data class HudApprovalDialogState(
        val approvalId: String,
        val stepIndex: Int,
        val totalSteps: Int,
        val riskType: HighRiskActionType,
        val targetApp: String,
        val actionTitle: String,
        val description: String,
        val contentPreview: String,
        val voiceHint: String = "Say \"Confirm\" to approve or \"Cancel\" to reject"
    )

    private val _currentPlan = MutableStateFlow<AgentTaskHistoryRepository.MultiTaskPlanRecord?>(null)
    val currentPlan: StateFlow<AgentTaskHistoryRepository.MultiTaskPlanRecord?> = _currentPlan.asStateFlow()

    private val _pendingApprovalDialog = MutableStateFlow<HudApprovalDialogState?>(null)
    val pendingApprovalDialog: StateFlow<HudApprovalDialogState?> = _pendingApprovalDialog.asStateFlow()

    fun buildHudApprovalDialogState(
        step: AgentTaskHistoryRepository.PlanStepRecord,
        description: String
    ): HudApprovalDialogState {
        return HudApprovalDialogState(
            approvalId = UUID.randomUUID().toString(),
            stepIndex = step.stepIndex,
            totalSteps = step.totalSteps,
            riskType = HighRiskActionType.SOCIAL_MEDIA_POST,
            targetApp = step.targetApp,
            actionTitle = step.title,
            description = description,
            contentPreview = "Target: ${step.targetApp}\nAction: ${step.title}"
        )
    }

    fun executeVoiceCommandPlan(
        context: Context,
        command: String,
        onSpoken: ((String) -> Unit)? = null
    ) {
        val clauses = command.split(Regex("(?:,|\\bthen\\b|\\bafter that\\b|\\band\\b)", RegexOption.IGNORE_CASE))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .ifEmpty { listOf(command.trim()) }

        val total = clauses.size
        val steps = clauses.mapIndexed { idx, clause ->
            val lower = clause.lowercase()
            val targetApp = when {
                lower.contains("facebook") -> "Facebook"
                lower.contains("instagram") -> "Instagram"
                lower.contains("whatsapp") -> "WhatsApp"
                lower.contains("youtube") -> "YouTube"
                else -> "System"
            }
            val needsApproval = lower.contains("comment") || lower.contains("delete") || lower.contains("photo") || lower.contains("send")
            val status = when {
                idx == 0 && !needsApproval -> AgentTaskHistoryRepository.StepStatus.RUNNING
                idx == 0 && needsApproval -> AgentTaskHistoryRepository.StepStatus.WAITING_FOR_APPROVAL
                else -> AgentTaskHistoryRepository.StepStatus.PENDING
            }
            AgentTaskHistoryRepository.PlanStepRecord(
                stepIndex = idx + 1,
                totalSteps = total,
                title = clause,
                targetApp = targetApp,
                status = status,
                verificationNote = if (needsApproval) "High-risk action requires user approval" else "Queued for execution"
            )
        }

        val plan = AgentTaskHistoryRepository.MultiTaskPlanRecord(
            planId = UUID.randomUUID().toString(),
            originalVoiceCommand = command,
            overallStatus = if (steps.any { it.status == AgentTaskHistoryRepository.StepStatus.WAITING_FOR_APPROVAL }) "PAUSED_FOR_APPROVAL" else "RUNNING",
            steps = steps
        )
        _currentPlan.value = plan
        val waitingStep = steps.firstOrNull { it.status == AgentTaskHistoryRepository.StepStatus.WAITING_FOR_APPROVAL }
        if (waitingStep != null) {
            _pendingApprovalDialog.value = buildHudApprovalDialogState(waitingStep, "Please confirm before Jarvic executes this action.")
        }
        onSpoken?.invoke("Queued $total tasks for execution.")
    }

    fun approveWaitingStepAndContinue(
        context: Context,
        onSpoken: ((String) -> Unit)? = null
    ) {
        val plan = _currentPlan.value ?: return
        val updatedSteps = plan.steps.map { step ->
            if (step.status == AgentTaskHistoryRepository.StepStatus.WAITING_FOR_APPROVAL ||
                step.status == AgentTaskHistoryRepository.StepStatus.RUNNING
            ) {
                step.copy(status = AgentTaskHistoryRepository.StepStatus.COMPLETED, verificationNote = "Approved & completed")
            } else {
                step
            }
        }
        _pendingApprovalDialog.value = null
        _currentPlan.value = plan.copy(overallStatus = "COMPLETED", steps = updatedSteps)
        onSpoken?.invoke("Action approved and executed.")
    }

    fun rejectWaitingStep(
        context: Context,
        onSpoken: ((String) -> Unit)? = null
    ) {
        val plan = _currentPlan.value ?: return
        val updatedSteps = plan.steps.map { step ->
            if (step.status == AgentTaskHistoryRepository.StepStatus.WAITING_FOR_APPROVAL) {
                step.copy(status = AgentTaskHistoryRepository.StepStatus.SKIPPED, verificationNote = "Rejected by user")
            } else {
                step
            }
        }
        _pendingApprovalDialog.value = null
        _currentPlan.value = plan.copy(overallStatus = "CANCELLED", steps = updatedSteps)
        onSpoken?.invoke("High-risk action cancelled.")
    }

    fun resumeFromLastCompletedTask(
        context: Context,
        onSpoken: ((String) -> Unit)? = null
    ) {
        val plan = _currentPlan.value ?: return
        val updatedSteps = plan.steps.map { step ->
            if (step.status == AgentTaskHistoryRepository.StepStatus.PENDING) {
                step.copy(status = AgentTaskHistoryRepository.StepStatus.RUNNING)
            } else {
                step
            }
        }
        _currentPlan.value = plan.copy(overallStatus = "RUNNING", steps = updatedSteps)
        onSpoken?.invoke("Resuming task queue.")
    }

    fun cancelActivePlan(
        context: Context,
        onSpoken: ((String) -> Unit)? = null
    ) {
        _pendingApprovalDialog.value = null
        _currentPlan.value = null
        onSpoken?.invoke("Stopped active task queue.")
    }
}
