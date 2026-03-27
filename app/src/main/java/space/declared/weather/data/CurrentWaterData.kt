package space.declared.weather.data

data class CurrentWaterData(
    val stationId: String,
    val stationName: String,
    val flowRateCfs: Double?,    // Streamflow in Cubic Feet per Second
    val gaugeHeightFt: Double?    // Gauge Height in Feet
)