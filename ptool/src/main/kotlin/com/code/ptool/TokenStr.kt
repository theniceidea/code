package com.code.ptool

import cn.hutool.core.util.CharUtil

class TokenStr(var str:String, val ignoreCase:Boolean=false) {
    var index:Int=0
    fun next(delim:String): String {
        val strLen=str.length
        if(index>=strLen){
            return ""
        }
        var i=index
        val delimLen=delim.length
        var continueWhile=false
        while(true){
            for(si in 0 until delimLen){
                val i1 = i + si
                if(i1>=strLen){
                    val ret = str.substring(index)
                    index=strLen
                    return ret
                }
                if(CharUtil.equals(str[i1], delim[si], ignoreCase)){
                    continue
                }else{
                    i += 1
                    continueWhile=true
                    break
                }
            }
            if(continueWhile){
                continueWhile=false
                continue
            }
            val ret=str.substring(index, i)
            index=i+delimLen
            return ret
        }
    }
    fun nextBlankThrow(delim:String, throwMsg:String): String {
        val next = this.next(delim)
        if(next.isBlank()){
            throw RuntimeException(throwMsg)
        }
        return next
    }
    fun nextAll(): String {
        if(index>=str.length){
            return ""
        }
        return str.substring(index)
    }
    fun isEnd(): Boolean {
        return index>=str.length
    }
}