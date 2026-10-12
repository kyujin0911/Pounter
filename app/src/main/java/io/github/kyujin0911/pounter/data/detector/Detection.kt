package io.github.kyujin0911.pounter.data.detector

import io.github.kyujin0911.pounter.core.model.Marker

data class Detection(val markers: List<Marker>, val millis: Long)
