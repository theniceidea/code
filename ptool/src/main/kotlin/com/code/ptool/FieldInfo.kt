package com.code.ptool

import com.google.common.base.CaseFormat

class FieldInfo(val name:String
                , val insertGenerate:String
                , val dataType:String
                , val annos:List<AnnoInfo>
                , val enumInfo:EnumInfo?
                , val ref:String
                , val refColumn:String
                , val comment:String) {
    var lowerCamelName=""
    var upperCamelName=""
    init {
        lowerCamelName= CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, name)
        upperCamelName= CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, name)
    }
    fun getAnno(name:String): AnnoInfo? {
        return annos.firstOrNull { it.name == name }
    }
}