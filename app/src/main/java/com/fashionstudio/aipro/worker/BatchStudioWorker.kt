package com.fashionstudio.aipro.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fashionstudio.aipro.core.ai.model.GenerationConfig
import com.fashionstudio.aipro.core.database.dao.BatchJobDao
import com.fashionstudio.aipro.core.database.dao.ProjectDao
import com.fashionstudio.aipro.domain.StudioPipelineUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Resilient WorkManager batch processor with per-job budget caps, rate-limit pacing,
 * intermediate persistence, and recovery across app restarts.
 */
@HiltWorker
class BatchStudioWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val batchJobDao: BatchJobDao,
    private val projectDao: ProjectDao,
    private val pipeline: StudioPipelineUseCase
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_BATCH_JOB_ID = "batch_job_id"
    }

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun doWork(): Result {
        val jobId = inputData.getString(KEY_BATCH_JOB_ID) ?: return Result.failure()
        var job = batchJobDao.getById(jobId) ?: return Result.failure()
        if (job.status == "CANCELLED" || job.status == "PAUSED") return Result.success()

        job = job.copy(status = "RUNNING", updatedAtMillis = System.currentTimeMillis())
        batchJobDao.upsert(job)

        val projectIds = runCatching {
            json.decodeFromString(ListSerializer(String.serializer()), job.itemProjectIdsJson)
        }.getOrDefault(emptyList())

        var completed = job.completedItems
        var failed = job.failedItems
        var spent = job.spentUsd

        for (projectId in projectIds.drop(completed + failed)) {
            if (isStopped) {
                batchJobDao.upsert(job.copy(status = "PAUSED", completedItems = completed, failedItems = failed, spentUsd = spent))
                return Result.success()
            }
            if (job.budgetLimitUsd > 0 && spent >= job.budgetLimitUsd) {
                batchJobDao.upsert(job.copy(status = "BUDGET_STOPPED", completedItems = completed, failedItems = failed, spentUsd = spent))
                return Result.success()
            }

            val project = projectDao.getById(projectId)
            if (project == null) {
                failed++
                continue
            }

            val config = runCatching {
                json.decodeFromString(GenerationConfig.serializer(), project.generationConfigJson)
            }.getOrDefault(GenerationConfig())

            val outcome = pipeline.runStudioGeneration(projectId, config, runAiQualityAudit = false)
            if (outcome.isSuccess) {
                completed++
                spent += outcome.getOrNull()?.version?.estimatedCostUsd ?: 0.0
            } else {
                failed++
            }

            batchJobDao.upsert(
                job.copy(
                    completedItems = completed,
                    failedItems = failed,
                    spentUsd = spent,
                    updatedAtMillis = System.currentTimeMillis()
                )
            )
            // Respect API rate limits between sequential batch items
            delay(1500L)
        }

        batchJobDao.upsert(
            job.copy(
                status = "COMPLETED",
                completedItems = completed,
                failedItems = failed,
                spentUsd = spent,
                updatedAtMillis = System.currentTimeMillis()
            )
        )
        return Result.success()
    }
}
