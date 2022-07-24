package com.code.ptool.db

import com.code.ptool.TokenStr

class SqlLine(val lineNumber:Int, val line:String, var sqlItem: SqlItem?) {
    private val regex = Regex("\\:[a-zA-Z_][a-zA-Z0-9_]+")
    var paramNames=ArrayList<String>()
    val checkFuns=ArrayList<String>()
    var errMsg=""
    var comment=""
    init {
        if(this.line.startsWith("#java ")){
            parseJavaLine()
        }else{
            parseSqlLine()
        }
    }
    private fun parseJavaLine(){
        var result = regex.find(this.line)
        while(null!=result){
            var element = result.value.substring(1)
            if(paramNames.contains(element)){
                result = result.next()
                continue
            }
            paramNames.add(element)
            result = result.next()
        }
    }
    private fun parseSqlLine(){
        var result = regex.find(this.line)
        if(null!=result){
            paramNames.add(result.value.substring(1))
            if(null != result.next()){
                throw RuntimeException("sql文件 ${this.sqlItem!!.mSqlInfo.name} 第${this.lineNumber}行 一行只允许一个参数")
            }
            val tokenStr = TokenStr(this.line.substringAfter("#v ", "").trim())
            while(!tokenStr.isEnd()) {
                var token = tokenStr.next(" ").trim()
                if(token.startsWith("Err_")){
                    errMsg=token
                    continue
                }
                if (token.startsWith("comment:")) {
                    comment = token.substring(8)
                    break
                }
                if(token.isNotBlank() && !checkFuns.contains(token)) {
                    checkFuns.add(token)
                }
            }
        }
    }
}