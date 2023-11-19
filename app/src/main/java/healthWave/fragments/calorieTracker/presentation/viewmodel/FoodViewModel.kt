package healthWave.fragments.calorieTracker.presentation.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.healthwave.R
import dagger.hilt.android.lifecycle.HiltViewModel
import healthWave.core.util.HelperFunctions.Companion.getCurrentDateAsString
import healthWave.core.util.UiEvent
import healthWave.core.util.UiText
import healthWave.data.local.database.entity.Water
import healthWave.fragments.calorieTracker.domain.model.CalorieAndNutrientSummary
import healthWave.fragments.calorieTracker.domain.model.Food
import healthWave.fragments.calorieTracker.domain.model.FoodNutrimentsInfo
import healthWave.fragments.calorieTracker.domain.model.MealType
import healthWave.fragments.calorieTracker.domain.useCase.FoodUseCases
import healthWave.fragments.calorieTracker.presentation.event.CalorieTrackerEvent
import healthWave.fragments.calorieTracker.state.FoodNutrimentsInfoState
import healthWave.fragments.calorieTracker.state.FoodState
import healthWave.fragments.calorieTracker.state.OverviewState
import healthWave.fragments.calorieTracker.state.WaterState
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FoodViewModel @Inject constructor(
    private val foodUseCases: FoodUseCases
) : ViewModel() {

    var foodState by mutableStateOf(FoodState())
        private set

    var overviewState by mutableStateOf(OverviewState())
        private set

    private var shouldEmitValue by mutableStateOf(true)
    // A flag to prevent emitting a new value after insert in database

    private var getFoodByDateJob: Job? = null
    private var getFoodByNameJob: Job? = null
    private var getWaterByDateJob: Job? = null

    private val _uiEvent = Channel<UiEvent>()
    val uiEvent = _uiEvent.receiveAsFlow()

    init {
        getFoodOverviewByDate(date = getCurrentDateAsString())
    }

    fun onEvent(event: CalorieTrackerEvent) {
        when (event) {
            is CalorieTrackerEvent.CalculateCaloriesAndNutrients -> calculateCaloriesAndNutrients(
                foods = event.foods
            )

            is CalorieTrackerEvent.ChangeFoodAmount -> changeFoodAmount(
                food = event.food,
                amount = event.amount
            )

            is CalorieTrackerEvent.ChangeWaterMilliliters -> changeWaterMilliliters(
                newMilliliters = event.newMilliliters
            )

            is CalorieTrackerEvent.DeleteFood -> deleteFood(
                date = event.date,
                food = event.food
            )

            is CalorieTrackerEvent.DeleteWater -> deleteWater(
                date = event.date,
                water = event.water
            )

            is CalorieTrackerEvent.FoodItemHeaderExpanded -> foodItemHeaderExpanded(
                food = event.food
            )

            is CalorieTrackerEvent.GetFoodByDate -> getFoodByDate(
                date = event.date,
                mealType = event.mealType
            )

            is CalorieTrackerEvent.GetFoodOverviewByDate -> getFoodOverviewByDate(
                date = event.date
            )

            is CalorieTrackerEvent.GetWaterByDate -> getWaterByDate(
                date = event.date
            )

            is CalorieTrackerEvent.InsertFood -> insertFood(
                date = event.date,
                food = event.food,
                mealType = event.mealType
            )

            is CalorieTrackerEvent.InsertWater -> insertWater(
                date = event.date,
                water = event.water
            )

            is CalorieTrackerEvent.OnQueryChange -> onQueryChange(
                query = event.query
            )

            is CalorieTrackerEvent.ResetRememberedState -> resetRememberedState(
                flag = event.flag
            )

            is CalorieTrackerEvent.SetEatingOccasionItemCardExpanded -> setEatingOccasionItemCardExpanded(
                flag = event.flag
            )

            is CalorieTrackerEvent.ViewMyMeals -> viewMyMeals(
                date = event.date,
                mealType = event.mealType
            )

            CalorieTrackerEvent.OnSearch -> onSearch()
            CalorieTrackerEvent.EatingOccasionItemExpanded -> eatingOccasionItemExpanded()
        }
    }

    private fun getFoodOverviewByDate(
        date: String
    ) {
        overviewState = overviewState.copy(
            isLoading = true,
            overallCalories = 0,
            breakfastCalories = 0,
            lunchCalories = 0,
            dinnerCalories = 0,
            snackCalories = 0,
            overallCarbs = 0,
            overallProteins = 0,
            overallFats = 0,
            overallWaterIntake = 0
        )
        getFoodByDateJob?.cancel()
        getFoodByDateJob = foodUseCases
            .getFoodByDate(date)
            .onEach { foods ->
                if (foods.isEmpty()) {
                    overviewState = overviewState.copy(
                        isLoading = false
                    )
                    return@onEach
                }
                val summary = calculateCaloriesAndNutrients(foods)

                // Fetch overall calories
                val overallCalories = summary.calorieMap.values.sum()

                // Fetch overall nutrients
                val overallCarbs = summary.nutrientMap["carbs"] ?: 0
                val overallProteins = summary.nutrientMap["proteins"] ?: 0
                val overallFats = summary.nutrientMap["fats"] ?: 0

                // Map individual meal type calories
                val breakfastCalories = summary.calorieMap[MealType.Breakfast] ?: 0
                val lunchCalories = summary.calorieMap[MealType.Lunch] ?: 0
                val dinnerCalories = summary.calorieMap[MealType.Dinner] ?: 0
                val snackCalories = summary.calorieMap[MealType.Snack] ?: 0

                overviewState = overviewState.copy(
                    overallCalories = overallCalories,
                    breakfastCalories = breakfastCalories,
                    lunchCalories = lunchCalories,
                    dinnerCalories = dinnerCalories,
                    snackCalories = snackCalories,
                    overallCarbs = overallCarbs,
                    overallProteins = overallProteins,
                    overallFats = overallFats
                )
            }.launchIn(viewModelScope)

        getWaterByDateJob?.cancel()
        getWaterByDateJob = foodUseCases
            .getWaterByDate(date)
            .onEach { waters ->
                if (waters.isEmpty()) {
                    overviewState = overviewState.copy(
                        isLoading = false
                    )
                    return@onEach
                }
                var overallWaterIntake = 0

                waters.forEach { water ->
                    overallWaterIntake += water.milliliters.toInt()
                }
                overviewState = overviewState.copy(
                    isLoading = false,
                    overallWaterIntake = overallWaterIntake
                )
            }.launchIn(viewModelScope)
    }

    private fun calculateCaloriesAndNutrients(
        foods: List<Food>
    ): CalorieAndNutrientSummary {
        val calorieMap = foods
            .groupBy { it.mealType }
            .mapValues { (_, foods) -> foods.sumOf { it.calories } }

        val nutrientMap = mutableMapOf<String, Int>()

        foods.forEach { food ->
            nutrientMap["proteins"] = (nutrientMap["proteins"] ?: 0) + food.protein
            nutrientMap["fats"] = (nutrientMap["fats"] ?: 0) + food.fat
            nutrientMap["carbs"] = (nutrientMap["carbs"] ?: 0) + food.carbs
        }

        return CalorieAndNutrientSummary(calorieMap, nutrientMap)
    }

    private fun getWaterByDate(
        date: String
    ) {
        foodState = foodState.copy(
            isLoading = true,
            waterIntakeInfo = emptyList()
        )

        getWaterByDateJob = foodUseCases
            .getWaterByDate(date)
            .onEach { waters ->
                foodState = foodState.copy(
                    isLoading = false,
                    waterIntakeInfo = waters.map { water ->
                        WaterState(
                            id = water.id,
                            milliliters = water.milliliters
                        )
                    }
                )
            }.launchIn(viewModelScope)
    }

    private fun deleteWater(
        date: String,
        water: WaterState
    ) {
        viewModelScope.launch {
            water.id?.let { foodUseCases.deleteWaterById(it) }

            val updatedWaterIntakeInfo = foodState.waterIntakeInfo.filter {
                it.id != water.id
            }
            foodState = foodState.copy(waterIntakeInfo = updatedWaterIntakeInfo)
            _uiEvent.send(
                UiEvent.ShowToast(
                    UiText.StringResource(resId = R.string.successfully_deleted)
                )
            )
            getFoodOverviewByDate(date = date)
        }
    }

    private fun insertWater(
        date: String,
        water: WaterState
    ) {
        viewModelScope.launch {

            foodUseCases.insertWater(
                water = Water(
                    milliliters = water.milliliters.takeIf { it.isNotEmpty() } ?: return@launch,
                    date = date
                )
            )
            _uiEvent.send(
                UiEvent.ShowToast(
                    UiText.StringResource(resId = R.string.successfully_added)
                )
            )
        }
    }

    private fun getFoodByDate(
        date: String,
        mealType: MealType = MealType.fromString("")
    ) {
        getWaterByDateJob?.cancel()
        foodState = foodState.copy(
            isLoading = true,
            foodNutrimentsInfo = emptyList()
        )

        getFoodByDateJob = foodUseCases
            .getFoodByDate(date)
            .onEach { foods ->
                val filteredFoods =
                    if (mealType.name != "" && mealType.name != "All meals") {
                        foods.filter { it.mealType.name == mealType.name }
                    } else {
                        foods
                    }
                if (shouldEmitValue) {
                    foodState = foodState.copy(
                        foodNutrimentsInfo = filteredFoods.map { food ->
                            FoodNutrimentsInfoState(
                                food = FoodNutrimentsInfo(
                                    id = food.id,
                                    name = food.name,
                                    imageUrl = food.imageUrl,
                                    calories100g = food.calories,
                                    carbs100g = food.carbs,
                                    protein100g = food.protein,
                                    fat100g = food.fat
                                ),
                                amount = food.amount.toString()
                            )
                        },
                        isLoading = false,
                        query = ""
                    )
                }
            }.launchIn(viewModelScope)
    }

    private fun onQueryChange(query: String) {
        foodState = foodState.copy(query = query)
    }

    private fun eatingOccasionItemExpanded() {
        foodState = foodState.copy(
            isEatingOccasionItemCardExpanded = !foodState.isEatingOccasionItemCardExpanded
        )
    }

    private fun resetRememberedState(flag: Boolean) {
        if (flag) {
            foodState = foodState.copy(
                foodNutrimentsInfo = emptyList(),
                viewMyMeals = false
            )
        }
    }

    private fun setEatingOccasionItemCardExpanded(flag: Boolean) {
        foodState = foodState.copy(
            isEatingOccasionItemCardExpanded = flag
        )
    }

    private fun foodItemHeaderExpanded(
        food: FoodNutrimentsInfo
    ) {
        foodState = foodState.copy(
            foodNutrimentsInfo = foodState.foodNutrimentsInfo.map {
                if (it.food == food) {
                    it.copy(
                        isFoodItemHeaderExpanded = !it.isFoodItemHeaderExpanded
                    )
                } else it
            }
        )
    }

    private fun changeFoodAmount(
        food: FoodNutrimentsInfo,
        amount: String
    ) {
        foodState = foodState.copy(
            foodNutrimentsInfo = foodState.foodNutrimentsInfo.map {
                if (it.food == food) {
                    it.copy(amount = amount)
                } else it
            }
        )
    }

    private fun changeWaterMilliliters(
        newMilliliters: String
    ) {
        foodState = foodState.copy(
            takeWaterIntake = foodState.takeWaterIntake.copy(
                milliliters = newMilliliters
            )
        )
    }

    private fun viewMyMeals(
        date: String,
        mealType: MealType
    ) {
        shouldEmitValue = true
        foodState = foodState.copy(
            viewMyMeals = true
        )
        getFoodByDate(
            date = date,
            mealType = mealType
        )
    }

    private fun deleteFood(
        date: String,
        food: FoodNutrimentsInfo

    ) {
        viewModelScope.launch {
            food.id?.let { foodUseCases.deleteFoodById(it) }

            val updatedFoodNutrimentsInfo = foodState.foodNutrimentsInfo.filter {
                it.food.id != food.id
            }
            foodState = foodState.copy(foodNutrimentsInfo = updatedFoodNutrimentsInfo)
            _uiEvent.send(
                UiEvent.ShowToast(
                    UiText.StringResource(resId = R.string.successfully_deleted)
                )
            )
            getFoodOverviewByDate(date = date)
            shouldEmitValue = false
        }
    }

    private fun insertFood(
        date: String,
        food: FoodNutrimentsInfo,
        mealType: MealType
    ) {
        viewModelScope.launch {
            val uiState = foodState.foodNutrimentsInfo.find { it.food == food }

            foodUseCases.insertFood(
                food = uiState?.food ?: return@launch,
                amount = uiState.amount.toIntOrNull() ?: return@launch,
                mealType = mealType,
                date = date
            )

            _uiEvent.send(
                UiEvent.ShowToast(
                    UiText.StringResource(resId = R.string.successfully_added)
                )
            )
        }
        shouldEmitValue = false
    }

    private fun onSearch() {
        viewModelScope.launch {
            foodState = foodState.copy(
                isLoading = true,
                viewMyMeals = false,
                foodNutrimentsInfo = emptyList()
            )
            foodUseCases
                .searchFood(foodState.query)
                .onSuccess { foods ->
                    if (foods.isEmpty()) {
                        _uiEvent.send(
                            UiEvent.ShowToast(
                                UiText.StringResource(resId = R.string.no_food_found)
                            )
                        )
                    }
                    foodState = foodState.copy(
                        foodNutrimentsInfo = foods.map {
                            FoodNutrimentsInfoState(it)
                        },
                        isLoading = false,
                        query = ""
                    )
                }
                .onFailure {
                    shouldEmitValue = true
                    getFoodByNameJob?.cancel()
                    getFoodByNameJob = foodUseCases
                        .getFoodByName(foodState.query)
                        .onEach { foods ->
                            if (shouldEmitValue) {
                                if (foods.isEmpty()) {
                                    foodState = foodState.copy(
                                        isLoading = false,
                                        query = ""
                                    )
                                    _uiEvent.send(
                                        UiEvent.ShowToast(
                                            UiText.StringResource(resId = R.string.check_your_internet)
                                        )
                                    )
                                    return@onEach
                                }
                                foodState = foodState.copy(
                                    foodNutrimentsInfo = foods.map { food ->
                                        FoodNutrimentsInfoState(
                                            food = FoodNutrimentsInfo(
                                                id = food.id,
                                                name = food.name,
                                                imageUrl = food.imageUrl,
                                                calories100g = food.caloriesPer100g,
                                                carbs100g = food.carbsPer100g,
                                                protein100g = food.proteinPer100g,
                                                fat100g = food.fatPer100g
                                            )
                                        )
                                    },
                                    isLoading = false,
                                    query = ""
                                )
                            } else {
                                foodState = foodState.copy(
                                    isLoading = false,
                                    query = ""
                                )
                            }
                        }.launchIn(viewModelScope)
                }
        }
    }
}