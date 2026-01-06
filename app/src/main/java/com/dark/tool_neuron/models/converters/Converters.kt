package com.dark.tool_neuron.models.converters

import androidx.room.TypeConverter
import com.dark.tool_neuron.models.enums.PathType
import com.dark.tool_neuron.models.enums.ProviderType

class Converters {
    @TypeConverter
    fun fromProviderType(value: ProviderType): String = value.name
    
    @TypeConverter
    fun toProviderType(value: String): ProviderType = ProviderType.valueOf(value)
    
    @TypeConverter
    fun fromPathType(value: PathType): String = value.name
    
    @TypeConverter
    fun toPathType(value: String): PathType = PathType.valueOf(value)
}