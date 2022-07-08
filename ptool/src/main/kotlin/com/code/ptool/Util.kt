package com.code.ptool

object Util {
    fun convertJavaType(type:String): String {
        if(type=="int"){
            return "Integer"
        }else if(type=="long"){
            return "Long"
        }else if(type=="string"){
            return "String"
        }else if(type=="double"){
            return "Double"
        }else if(type=="decimal"){
            return "BigDecimal"
        }else if(type=="timestamp"){
            return "Timestamp"
        }else{
            throw RuntimeException("不支持的基本类型 : ${type}")
        }
    }
}