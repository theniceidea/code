package com.code.ptool.db

import com.code.ptool.Project
import com.code.ptool.TokenStr
import java.io.StringReader

class MSqlInfo(val name:String, val content:String, val project: Project) {
    var db=""
    var imps=ArrayList<String>()
    var items=ArrayList<SqlItem>()

    init{
        initItems()
    }

    private fun initItems(){
        val sr = StringReader(content)
        val lines = sr.readLines()
        var itmName=""
        var returnType=""
        var itmComment=""
        var publish=false
        var itmContent=ArrayList<SqlLine>()
        var lineNumber=0
        for(line in lines){
            lineNumber+=1
            if(""==db && line.startsWith("#db=")){
                db=line.substring(4).trim()
                continue
            }
            if(line.startsWith("#import ")){
                imps.add(line.substring(1))
                continue
            }
            if(line.startsWith("## ")){
                if("" != itmName && "" != returnType && itmContent.isNotEmpty()){
                    val element = SqlItem(itmName, returnType, publish, itmComment, itmContent, this)
                    for(itm in itmContent){
                        itm.sqlItem=element
                    }
                    items.add(element)
                }
                itmContent= ArrayList()
                var tokenStr = TokenStr(line.substring(3).trim())
                itmName=tokenStr.nextBlankThrow(" ", "sql文件 ${this.name} 第${lineNumber}行不符合格式")
                returnType=tokenStr.nextBlankThrow(" ", "sql文件 ${this.name} 第${lineNumber}行不符合格式")
                publish="publish"==tokenStr.nextBlankThrow(" ", "sql文件 ${this.name} 第${lineNumber}行不符合格式")
                itmComment=tokenStr.nextAll()
                continue
            }
            if(line.startsWith("#")){
                continue
            }
            if(""==itmName){
                continue
            }
            if(line.isBlank()){
                continue
            }
            itmContent.add(SqlLine(lineNumber, line, null))
        }
        if("" != itmName && "" != returnType && itmContent.isNotEmpty()){
            val element = SqlItem(itmName, returnType, publish, itmComment, itmContent, this)
            for(itm in itmContent){
                itm.sqlItem=element
            }
            items.add(element)
        }
    }
    fun buildCode(){
        for(itm in items){
            itm.buildCode()
        }
    }
}