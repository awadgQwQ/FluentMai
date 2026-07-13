package dev.fluentmai.android.core.model

import kotlin.math.ceil

enum class RatingRecommendationBucket(
    val bestLabel: String,
    internal val capacity: Int,
) {
    OLD("B35", 35),
    CURRENT("B15", 15),
}

enum class RatingRecommendationReason {
    TARGET_COMPLETED,
    ALREADY_IN_BEST_SET,
    ENTERS_BEST_SET,
    TIES_BEST_SET_CUTOFF,
    BELOW_BEST_SET_CUTOFF,
}

enum class RatingRecommendationAvailability {
    AVAILABLE,
    CURRENT_VERSION_UNAVAILABLE,
    NO_ELIGIBLE_SCORES,
}

data class RatingRecommendationFilters(
    val targetTotalRating: Int? = null,
    val targetAchievement: Double? = null,
    val constantMin: Double? = null,
    val constantMax: Double? = null,
    val versionAge: VersionAgeFilter = VersionAgeFilter.ALL,
    val excludeSssPlus: Boolean = true,
    val excludedIdentities: Set<ChartIdentity> = emptySet(),
    val onlyB50Gain: Boolean = true,
)

data class RatingRecommendation(
    val identity: ChartIdentity,
    val chart: ChartRecord,
    val score: ScoreRecord,
    val bucket: RatingRecommendationBucket,
    val currentAchievement: Double,
    val currentSingleRating: Int,
    val targetAchievement: Double,
    val targetSingleRating: Int,
    val theoreticalSingleGain: Int,
    val actualB50Gain: Int,
    val currentTotalRating: Int,
    val projectedTotalRating: Int,
    val bucketCutoffRating: Int,
    val wasInBestSet: Boolean,
    val willEnterBestSet: Boolean,
    val isCompleted: Boolean,
    val reason: RatingRecommendationReason,
)

data class RatingRecommendationResult(
    val availability: RatingRecommendationAvailability,
    val currentTotalRating: Int,
    val oldBestCutoff: Int?,
    val currentBestCutoff: Int?,
    val eligiblePlayedCharts: Int,
    val recommendations: List<RatingRecommendation>,
)

/**
 * Builds deterministic suggestions from local best scores and the validated chart catalog.
 *
 * The function never estimates player skill. A suggestion is only a transparent simulation of
 * a concrete achievement target and its effect on the current B35/B15 cutoffs.
 */
fun buildRatingRecommendations(
    records: List<PlayerChartRecord>,
    currentVersion: MaimaiCurrentVersion?,
    filters: RatingRecommendationFilters = RatingRecommendationFilters(),
): RatingRecommendationResult {
    validateRecommendationFilters(filters)
    if (currentVersion == null) {
        return RatingRecommendationResult(
            availability = RatingRecommendationAvailability.CURRENT_VERSION_UNAVAILABLE,
            currentTotalRating = 0,
            oldBestCutoff = null,
            currentBestCutoff = null,
            eligiblePlayedCharts = 0,
            recommendations = emptyList(),
        )
    }

    val candidates = records.mapNotNull { record -> record.toRecommendationCandidate(currentVersion) }
    if (candidates.isEmpty()) {
        return RatingRecommendationResult(
            availability = RatingRecommendationAvailability.NO_ELIGIBLE_SCORES,
            currentTotalRating = 0,
            oldBestCutoff = null,
            currentBestCutoff = null,
            eligiblePlayedCharts = 0,
            recommendations = emptyList(),
        )
    }

    val bucketStates = RatingRecommendationBucket.entries.associateWith { bucket ->
        val bucketCandidates = candidates.filter { it.bucket == bucket }.sortedWith(recommendationCandidateComparator)
        val best = bucketCandidates.take(bucket.capacity)
        RecommendationBucketState(
            candidates = bucketCandidates,
            best = best,
            bestKeys = best.mapTo(mutableSetOf()) { it.identity },
            totalRating = best.sumOf { it.rating },
            cutoffRating = best.takeIf { it.size == bucket.capacity }?.lastOrNull()?.rating ?: 0,
            hasFullBestSet = best.size == bucket.capacity,
        )
    }
    val currentTotalRating = bucketStates.values.sumOf { it.totalRating }
    val requirementsSpecified = filters.targetTotalRating != null || filters.targetAchievement != null

    val recommendations = candidates.asSequence()
        .filter { candidate -> candidate.matches(filters) }
        .mapNotNull { candidate ->
            val bucketState = bucketStates.getValue(candidate.bucket)
            val wasInBestSet = candidate.identity in bucketState.bestKeys
            val completed = if (requirementsSpecified) {
                (filters.targetTotalRating == null || currentTotalRating >= filters.targetTotalRating) &&
                    (filters.targetAchievement == null || candidate.achievement >= filters.targetAchievement)
            } else {
                candidate.achievement >= MAX_RECOMMENDATION_RATING_ACHIEVEMENT
            }

            val resolvedTargetAchievement = when {
                completed -> filters.targetAchievement ?: candidate.achievement
                else -> candidate.resolveTargetAchievement(
                    filters = filters,
                    currentTotalRating = currentTotalRating,
                    bucketState = bucketState,
                    wasInBestSet = wasInBestSet,
                    useDefaultMilestone = !requirementsSpecified,
                ) ?: return@mapNotNull null
            }
            val targetAchievement = if (completed) {
                resolvedTargetAchievement.coerceIn(0.0, MAX_ACHIEVEMENT)
            } else {
                resolvedTargetAchievement.coerceIn(
                    candidate.achievement.coerceAtMost(MAX_ACHIEVEMENT),
                    MAX_ACHIEVEMENT,
                )
            }

            val targetRating = if (completed) {
                candidate.rating
            } else {
                calculateDxRating(candidate.levelValue, targetAchievement)
            }
            val targetCandidate = candidate.copy(
                achievement = targetAchievement,
                rating = targetRating,
            )
            val simulatedBest = bucketState.candidates
                .map { existing -> if (existing.identity == candidate.identity) targetCandidate else existing }
                .sortedWith(recommendationCandidateComparator)
                .take(candidate.bucket.capacity)
            val simulatedBucketTotal = simulatedBest.sumOf { it.rating }
            val projectedTotalRating = currentTotalRating - bucketState.totalRating + simulatedBucketTotal
            val actualB50Gain = (projectedTotalRating - currentTotalRating).coerceAtLeast(0)
            val willEnterBestSet = simulatedBest.any { it.identity == candidate.identity }
            val theoreticalSingleGain = (targetRating - candidate.rating).coerceAtLeast(0)

            if (
                !completed &&
                filters.targetTotalRating != null &&
                currentTotalRating < filters.targetTotalRating &&
                projectedTotalRating < filters.targetTotalRating
            ) {
                return@mapNotNull null
            }
            if (filters.onlyB50Gain && actualB50Gain <= 0) return@mapNotNull null

            val reason = when {
                completed -> RatingRecommendationReason.TARGET_COMPLETED
                wasInBestSet -> RatingRecommendationReason.ALREADY_IN_BEST_SET
                actualB50Gain > 0 -> RatingRecommendationReason.ENTERS_BEST_SET
                willEnterBestSet -> RatingRecommendationReason.TIES_BEST_SET_CUTOFF
                else -> RatingRecommendationReason.BELOW_BEST_SET_CUTOFF
            }
            RatingRecommendation(
                identity = candidate.identity,
                chart = candidate.record.chart,
                score = candidate.record.score ?: return@mapNotNull null,
                bucket = candidate.bucket,
                currentAchievement = candidate.achievement,
                currentSingleRating = candidate.rating,
                targetAchievement = targetAchievement,
                targetSingleRating = targetRating,
                theoreticalSingleGain = theoreticalSingleGain,
                actualB50Gain = actualB50Gain,
                currentTotalRating = currentTotalRating,
                projectedTotalRating = projectedTotalRating,
                bucketCutoffRating = bucketState.cutoffRating,
                wasInBestSet = wasInBestSet,
                willEnterBestSet = willEnterBestSet,
                isCompleted = completed,
                reason = reason,
            )
        }
        .sortedWith(
            compareByDescending<RatingRecommendation> { it.actualB50Gain }
                .thenByDescending { it.theoreticalSingleGain }
                .thenBy { it.targetAchievement - it.currentAchievement }
                .thenByDescending { it.targetSingleRating }
                .thenByDescending { it.chart.levelValue ?: -1.0 }
                .thenBy { it.chart.title }
                .thenBy { it.identity.stableKey() },
        )
        .toList()

    return RatingRecommendationResult(
        availability = RatingRecommendationAvailability.AVAILABLE,
        currentTotalRating = currentTotalRating,
        oldBestCutoff = bucketStates.getValue(RatingRecommendationBucket.OLD).cutoffRating
            .takeIf { bucketStates.getValue(RatingRecommendationBucket.OLD).hasFullBestSet },
        currentBestCutoff = bucketStates.getValue(RatingRecommendationBucket.CURRENT).cutoffRating
            .takeIf { bucketStates.getValue(RatingRecommendationBucket.CURRENT).hasFullBestSet },
        eligiblePlayedCharts = candidates.size,
        recommendations = recommendations,
    )
}

private data class RecommendationCandidate(
    val record: PlayerChartRecord,
    val identity: ChartIdentity,
    val bucket: RatingRecommendationBucket,
    val levelValue: Double,
    val achievement: Double,
    val rating: Int,
)

private data class RecommendationBucketState(
    val candidates: List<RecommendationCandidate>,
    val best: List<RecommendationCandidate>,
    val bestKeys: Set<ChartIdentity>,
    val totalRating: Int,
    val cutoffRating: Int,
    val hasFullBestSet: Boolean,
)

private fun PlayerChartRecord.toRecommendationCandidate(
    currentVersion: MaimaiCurrentVersion,
): RecommendationCandidate? {
    val playerScore = score ?: return null
    val constant = chart.levelValue?.takeIf { it.isFinite() && it > 0.0 } ?: return null
    val bucket = when (chart.ratingBucket(currentVersion)) {
        MaimaiRatingBucket.OLD -> RatingRecommendationBucket.OLD
        MaimaiRatingBucket.CURRENT -> RatingRecommendationBucket.CURRENT
        MaimaiRatingBucket.INELIGIBLE -> return null
    }
    return RecommendationCandidate(
        record = this,
        identity = identity,
        bucket = bucket,
        levelValue = constant,
        achievement = playerScore.achievement.coerceIn(0.0, MAX_ACHIEVEMENT),
        rating = rating ?: calculateDxRating(constant, playerScore.achievement),
    )
}

private fun RecommendationCandidate.matches(filters: RatingRecommendationFilters): Boolean {
    if (record.chart.isDisabled == true || record.chart.isLocked == true) return false
    if (identity in filters.excludedIdentities) return false
    if (filters.excludeSssPlus && achievement >= MAX_RECOMMENDATION_RATING_ACHIEVEMENT) return false
    if (filters.constantMin?.let { levelValue < it } == true) return false
    if (filters.constantMax?.let { levelValue > it } == true) return false
    return when (filters.versionAge) {
        VersionAgeFilter.ALL -> true
        VersionAgeFilter.CURRENT -> bucket == RatingRecommendationBucket.CURRENT
        VersionAgeFilter.OLD -> bucket == RatingRecommendationBucket.OLD
    }
}

private fun RecommendationCandidate.resolveTargetAchievement(
    filters: RatingRecommendationFilters,
    currentTotalRating: Int,
    bucketState: RecommendationBucketState,
    wasInBestSet: Boolean,
    useDefaultMilestone: Boolean,
): Double? {
    var target = achievement
    if (useDefaultMilestone) {
        target = nextRecommendationMilestone(achievement) ?: MAX_RECOMMENDATION_RATING_ACHIEVEMENT
    }
    filters.targetAchievement?.takeIf { it > achievement }?.let { target = maxOf(target, it) }

    filters.targetTotalRating?.takeIf { it > currentTotalRating }?.let { requestedTotal ->
        val requiredGain = requestedTotal - currentTotalRating
        val requiredSingleRating = if (wasInBestSet) {
            rating + requiredGain
        } else {
            bucketState.cutoffRating + requiredGain
        }
        val achievementForRating = minimumAchievementForRating(
            levelValue = levelValue,
            requiredRating = requiredSingleRating,
            lowerBound = target,
        ) ?: return null
        target = maxOf(target, achievementForRating)
    }
    return target.takeIf { it <= MAX_ACHIEVEMENT }
}

private fun nextRecommendationMilestone(achievement: Double): Double? =
    RECOMMENDATION_MILESTONES.firstOrNull { it > achievement + ACHIEVEMENT_EPSILON }

private fun minimumAchievementForRating(
    levelValue: Double,
    requiredRating: Int,
    lowerBound: Double,
): Double? {
    if (calculateDxRating(levelValue, MAX_RECOMMENDATION_RATING_ACHIEVEMENT) < requiredRating) return null
    var low = ceil(lowerBound.coerceIn(0.0, MAX_RECOMMENDATION_RATING_ACHIEVEMENT) * ACHIEVEMENT_SCALE)
        .toInt()
    var high = (MAX_RECOMMENDATION_RATING_ACHIEVEMENT * ACHIEVEMENT_SCALE).toInt()
    while (low < high) {
        val middle = low + (high - low) / 2
        val achievement = middle.toDouble() / ACHIEVEMENT_SCALE
        if (calculateDxRating(levelValue, achievement) >= requiredRating) high = middle else low = middle + 1
    }
    return low.toDouble() / ACHIEVEMENT_SCALE
}

private fun validateRecommendationFilters(filters: RatingRecommendationFilters) {
    require(filters.targetTotalRating == null || filters.targetTotalRating in 0..30_000) {
        "目标总 Rating 必须在 0 到 30000 之间"
    }
    require(filters.targetAchievement == null || filters.targetAchievement.isFinite() && filters.targetAchievement in 0.0..MAX_ACHIEVEMENT) {
        "目标达成率必须在 0.0% 到 101.0% 之间"
    }
    require(filters.constantMin == null || filters.constantMin.isFinite() && filters.constantMin in 0.1..20.0) {
        "最低定数必须在 0.1 到 20.0 之间"
    }
    require(filters.constantMax == null || filters.constantMax.isFinite() && filters.constantMax in 0.1..20.0) {
        "最高定数必须在 0.1 到 20.0 之间"
    }
    require(filters.constantMin == null || filters.constantMax == null || filters.constantMin <= filters.constantMax) {
        "最低定数不能高于最高定数"
    }
}

private val recommendationCandidateComparator: Comparator<RecommendationCandidate> =
    compareByDescending<RecommendationCandidate> { it.rating }
        .thenByDescending { it.achievement }
        .thenByDescending { it.levelValue }
        .thenBy { it.record.chart.title }
        .thenBy { it.identity.stableKey() }

private val RECOMMENDATION_MILESTONES = listOf(97.0, 98.0, 99.0, 99.5, 100.0, 100.5)
private const val MAX_RECOMMENDATION_RATING_ACHIEVEMENT = 100.5
private const val MAX_ACHIEVEMENT = 101.0
private const val ACHIEVEMENT_SCALE = 10_000.0
private const val ACHIEVEMENT_EPSILON = 0.000_000_1
