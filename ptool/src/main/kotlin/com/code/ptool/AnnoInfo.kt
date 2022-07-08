package com.code.ptool

class AnnoInfo(val name:String, val attr:Map<String, Any>) {
    fun toJavaAnnoString(): String {
        val buf=StringBuffer()
        buf.append("@${name}")
        if(attr.isNotEmpty()){
            buf.append("(")
            var bln=false
            for(entry in attr){
                if(!bln){
                    bln=true
                }else{
                    buf.append(", ")
                }
                buf.append("${entry.key}=${entry.value}")
            }
            buf.append(")")
        }
        return buf.toString()
    }
}