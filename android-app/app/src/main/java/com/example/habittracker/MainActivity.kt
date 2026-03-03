package com.example.habittracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

data class Habit(val id: Int, val name: String)
data class HabitCreate(val name: String)
data class HabitEntryCreate(val habit_id: Int, val completed_on: String, val completed: Boolean = true)
data class MonthlySummary(
    val month: String,
    val total_habits: Int,
    val total_days_tracked: Int,
    val completion_rate_percent: Double,
)

interface HabitApi {
    @GET("habits")
    suspend fun listHabits(): List<Habit>

    @POST("habits")
    suspend fun addHabit(@Body payload: HabitCreate): Habit

    @POST("entries")
    suspend fun markToday(@Body payload: HabitEntryCreate)

    @GET("summary/{month}")
    suspend fun summary(@Path("month") month: String): MonthlySummary
}

class HabitRepository {
    private val api = Retrofit.Builder()
        .baseUrl("http://10.0.2.2:8000/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(HabitApi::class.java)

    suspend fun loadHabits(): List<Habit> = api.listHabits()
    suspend fun addHabit(name: String): Habit = api.addHabit(HabitCreate(name))
    suspend fun markToday(habitId: Int, isoDate: String) = api.markToday(HabitEntryCreate(habitId, isoDate))
    suspend fun monthlySummary(month: String): MonthlySummary = api.summary(month)
}

class HabitViewModel : ViewModel() {
    private val repository = HabitRepository()
    private val _habits = MutableStateFlow<List<Habit>>(emptyList())
    val habits: StateFlow<List<Habit>> = _habits

    private val _summary = MutableStateFlow<MonthlySummary?>(null)
    val summary: StateFlow<MonthlySummary?> = _summary

    fun refresh(month: String) {
        viewModelScope.launch {
            _habits.value = repository.loadHabits()
            _summary.value = repository.monthlySummary(month)
        }
    }

    fun addHabit(name: String, month: String) {
        viewModelScope.launch {
            repository.addHabit(name)
            refresh(month)
        }
    }

    fun markDone(habitId: Int, todayIso: String, month: String) {
        viewModelScope.launch {
            repository.markToday(habitId, todayIso)
            refresh(month)
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { HabitApp(HabitViewModel()) }
    }
}

@Composable
fun HabitApp(viewModel: HabitViewModel) {
    val month = "2026-01"
    val today = "2026-01-15"
    val habits by viewModel.habits.collectAsState()
    val summary by viewModel.summary.collectAsState()
    var newHabit by remember { mutableStateOf("") }

    androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.refresh(month) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Daily Habit Tracker", style = MaterialTheme.typography.headlineSmall)
        Text("Minimal UI: add habits, tick daily completion, review monthly summary.")

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newHabit,
                onValueChange = { newHabit = it },
                modifier = Modifier.weight(1f),
                label = { Text("New habit") },
            )
            Spacer(modifier = Modifier.padding(4.dp))
            Button(onClick = {
                if (newHabit.isNotBlank()) {
                    viewModel.addHabit(newHabit.trim(), month)
                    newHabit = ""
                }
            }) { Text("Add") }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(habits) { habit ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(habit.name)
                        var checked by remember { mutableStateOf(false) }
                        Switch(checked = checked, onCheckedChange = {
                            checked = it
                            if (it) viewModel.markDone(habit.id, today, month)
                        })
                    }
                }
            }
        }

        summary?.let {
            Spacer(Modifier.height(8.dp))
            Text("Month: ${it.month}")
            Text("Habits: ${it.total_habits}")
            Text("Tracked days: ${it.total_days_tracked}")
            Text("Completion: ${it.completion_rate_percent}%")
        }
    }
}
