package com.healthrings

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByDurationRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : AppCompatActivity() {

    companion object {
        val PERMISSIONS = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(DistanceRecord::class),
            HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        )
    }

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        lifecycleScope.launch {
            val client = HealthConnectClient.getOrCreate(this@MainActivity)
            val granted = client.permissionController.getGrantedPermissions()
            if (granted.containsAll(PERMISSIONS)) {
                loadData()
            } else {
                showMessage("Permission denied. Please grant Health Connect permissions.")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Date header
        val dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMM d"))
        findViewById<TextView>(R.id.dateText).text = dateStr
        findViewById<TextView>(R.id.greetingText).text = greeting()

        // Retry button
        findViewById<View>(R.id.btnRetry).setOnClickListener { checkAndLoad() }

        checkAndLoad()
    }

    private fun checkAndLoad() {
        when (HealthConnectClient.getSdkStatus(this)) {
            HealthConnectClient.SDK_UNAVAILABLE -> {
                showMessage("Health Connect is not available on this device.")
                return
            }
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                showMessage("Please update Health Connect from the Play Store.")
                return
            }
        }

        showLoading()
        lifecycleScope.launch {
            val client = HealthConnectClient.getOrCreate(this@MainActivity)
            val granted = client.permissionController.getGrantedPermissions()
            if (granted.containsAll(PERMISSIONS)) {
                loadData()
            } else {
                requestPermissions.launch(
                    client.permissionController.createRequestPermissionIntent(PERMISSIONS)
                )
            }
        }
    }

    private fun loadData() {
        val client = HealthConnectClient.getOrCreate(this)
        lifecycleScope.launch {
            try {
                val now = Instant.now()
                val todayStart = LocalDate.now()
                    .atStartOfDay(ZoneId.systemDefault()).toInstant()
                val weekStart = now.minus(6, ChronoUnit.DAYS)

                // Today's steps
                val stepsAgg = client.aggregate(
                    AggregateRequest(
                        setOf(StepsRecord.COUNT_TOTAL),
                        TimeRangeFilter.between(todayStart, now)
                    )
                )
                val steps = stepsAgg[StepsRecord.COUNT_TOTAL] ?: 0L

                // Today's calories
                val calAgg = client.aggregate(
                    AggregateRequest(
                        setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),
                        TimeRangeFilter.between(todayStart, now)
                    )
                )
                val calories = calAgg[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]
                    ?.inKilocalories?.toLong() ?: 0L

                // Today's distance
                val distAgg = client.aggregate(
                    AggregateRequest(
                        setOf(DistanceRecord.DISTANCE_TOTAL),
                        TimeRangeFilter.between(todayStart, now)
                    )
                )
                val distKm = distAgg[DistanceRecord.DISTANCE_TOTAL]
                    ?.inKilometers ?: 0.0

                // Weekly steps (last 7 days)
                val weeklyBuckets = client.aggregateGroupByDuration(
                    AggregateGroupByDurationRequest(
                        metrics = setOf(StepsRecord.COUNT_TOTAL),
                        timeRangeFilter = TimeRangeFilter.between(weekStart, now),
                        timeRangeSlicer = Duration.ofDays(1)
                    )
                )
                val weeklySteps = weeklyBuckets.map {
                    it.result[StepsRecord.COUNT_TOTAL] ?: 0L
                }

                updateUI(steps, calories, distKm, weeklySteps)

            } catch (e: Exception) {
                showMessage("Could not load data:\n${e.localizedMessage}")
            }
        }
    }

    private fun updateUI(
        steps: Long, calories: Long, distKm: Double, weeklySteps: List<Long>
    ) {
        val stepsGoal = 10_000L
        val calGoal = 500L

        // Show dashboard, hide others
        showDashboard()

        // Rings
        val rings = findViewById<RingsView>(R.id.ringsView)
        rings.stepsPct  = steps.toFloat() / stepsGoal
        rings.calPct    = calories.toFloat() / calGoal
        rings.movePct   = (steps.toFloat() / stepsGoal).coerceAtMost(1f)

        // Stats
        val pct = ((steps * 100) / stepsGoal).toInt().coerceAtMost(100)
        findViewById<TextView>(R.id.goalBadge).text = "$pct% of goal"
        findViewById<TextView>(R.id.stepsValue).text = "%,d".format(steps)
        findViewById<TextView>(R.id.calValue).text = "$calories"
        findViewById<TextView>(R.id.distValue).text = "%.1f".format(distKm)

        // Legend values
        findViewById<TextView>(R.id.legendStepsVal).text = "%,d / 10,000".format(steps)
        findViewById<TextView>(R.id.legendCalVal).text = "$calories / 500 kcal"
        findViewById<TextView>(R.id.legendMoveVal).text = "${(steps / 100).coerceAtMost(50)} / 50 min"

        // Weekly bar chart
        updateBars(weeklySteps, stepsGoal)
    }

    private fun updateBars(weeklySteps: List<Long>, goal: Long) {
        val container = findViewById<LinearLayout>(R.id.barsContainer)
        val maxVal = (weeklySteps.maxOrNull() ?: goal).coerceAtLeast(goal)
        val maxHeightPx = dpToPx(90)
        val barIds = listOf(
            R.id.bar0, R.id.bar1, R.id.bar2,
            R.id.bar3, R.id.bar4, R.id.bar5, R.id.bar6
        )
        barIds.forEachIndexed { i, id ->
            val bar = container.findViewById<View>(id) ?: return@forEachIndexed
            val s = weeklySteps.getOrElse(i) { 0L }
            val h = ((s.toFloat() / maxVal) * maxHeightPx).toInt().coerceAtLeast(dpToPx(3))
            bar.layoutParams = bar.layoutParams.also { it.height = h }
            bar.requestLayout()
        }
    }

    // ── Visibility helpers ─────────────────────────────────────────────────

    private fun showLoading() {
        findViewById<View>(R.id.loadingView).visibility  = View.VISIBLE
        findViewById<View>(R.id.dashboardView).visibility = View.GONE
        findViewById<View>(R.id.messageView).visibility  = View.GONE
    }

    private fun showDashboard() {
        findViewById<View>(R.id.loadingView).visibility  = View.GONE
        findViewById<View>(R.id.dashboardView).visibility = View.VISIBLE
        findViewById<View>(R.id.messageView).visibility  = View.GONE
    }

    private fun showMessage(msg: String) {
        findViewById<View>(R.id.loadingView).visibility  = View.GONE
        findViewById<View>(R.id.dashboardView).visibility = View.GONE
        findViewById<View>(R.id.messageView).visibility  = View.VISIBLE
        findViewById<TextView>(R.id.messageText).text = msg
    }

    // ── Util ───────────────────────────────────────────────────────────────

    private fun dpToPx(dp: Int) = (dp * resources.displayMetrics.density).toInt()

    private fun greeting(): String {
        return when (LocalTime.now().hour) {
            in 0..11  -> "Good morning"
            in 12..16 -> "Good afternoon"
            else      -> "Good evening"
        }
    }
}
