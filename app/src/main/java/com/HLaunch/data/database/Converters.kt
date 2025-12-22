package com.HLaunch.data.database

import androidx.room.TypeConverter
import com.HLaunch.data.entity.FileSource

class Converters {
    
    @TypeConverter
    fun fromFileSource(source: FileSource): String = source.name
    
    @TypeConverter
    fun toFileSource(value: String): FileSource = FileSource.valueOf(value)
}
