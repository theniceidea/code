package com.code.ptool

import cn.hutool.core.io.FileUtil
import cn.hutool.core.util.StrUtil
import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONArray
import com.alibaba.fastjson.JSONObject
import com.code.ptool.db.DbInfo
import com.code.ptool.db.MSqlInfo
import com.google.common.base.Preconditions
import java.io.File
import kotlin.io.path.Path

class Project(
    val name: String,
    val comp: String,
    val idTag: String,
    val metaDir: String,
    val entityPkg: String,
    val entityDir: String,
    val enumsPkg: String,
    val enumsDir: String,
    val queryPkg: String,
    val queryDir: String,
    val msqlPkg: String,
    val msqlDir: String,
    val msqlSummerPkg: String,
    val msqlSummerDir: String,
    val modelPkg: String,
    val modelDir: String
) {
    val tables=HashMap<String, TableInfo>()
    val dbs=HashMap<String, DbInfo>()
    val msqls=HashMap<String, MSqlInfo>()
    fun initTables(jaTables:JSONArray){
//        val loopFiles = FileUtil.loopFiles(metaDir) { pathname: File? -> pathname!!.name.endsWith(".json") }
        for(tbl in jaTables){
            if(tbl !is JSONObject){
                continue
            }
            val targetName = StrUtil.trimToEmpty(tbl.getString("targetName")).orEmpty()
//            Preconditions.checkState(StrUtil.isNotBlank(targetName), "project: ${this.name} 需要有table.targetName")
            val name = StrUtil.trimToEmpty(tbl.getString("name"))
            Preconditions.checkState(StrUtil.isNotBlank(name), "project: ${this.name} 需要有table.name")
            val idTag = StrUtil.trimToEmpty(tbl.getString("idTag"))
            Preconditions.checkState(StrUtil.isNotBlank(name), "project: ${this.name} table: name, 需要有idTag")
            val prefix = StrUtil.trimToEmpty(tbl.getString("prefix"))
            val fname=if(prefix.isNotEmpty())name.substring(prefix.length) else name
            val file = Path(metaDir, "tables", targetName, "${fname}.json").toFile()
            Preconditions.checkState(file.exists(), "没有找到表 ${fname} 结构定义文件")
            tables["${targetName}${name}"]=TableInfo(targetName, prefix, name, idTag, FileUtil.readUtf8String(file.absolutePath), this)
        }
    }
    fun initSqls(){
        val sqlMetaDir= Path(metaDir, "msqls").toFile().absolutePath
        val loopFiles = FileUtil.loopFiles(sqlMetaDir) { pathname: File? -> pathname!!.name.endsWith(".sql") }
        for(file in loopFiles){
            val fname = file.nameWithoutExtension
            val readText = file.readText(Charsets.UTF_8)
            msqls[fname] = MSqlInfo(fname, readText, this)
        }
    }
    fun build(){
        FileUtil.loopFiles(entityDir){ file-> FileUtil.del(file) }
        FileUtil.loopFiles(enumsDir){ file-> FileUtil.del(file) }
        FileUtil.loopFiles(modelDir){ file-> FileUtil.del(file) }
        FileUtil.loopFiles(queryDir){ file-> FileUtil.del(file) }
        FileUtil.loopFiles(msqlDir){ file-> FileUtil.del(file) }
        FileUtil.loopFiles(msqlSummerDir){ file-> FileUtil.del(file) }

        this.buildEntity()
        this.buildSql()
        this.buildEnumFiles()
        this.buildMsql()
    }
    fun buildEntity(){
        for(tbl in tables.values){
            tbl.buildEntity()
        }
    }
    fun buildSql(){
        for(tbl in tables.values){
            tbl.buildSql()
        }
    }
    fun buildMsql(){
        for(msql in msqls){
            msql.value.buildCode()
        }
    }
    fun buildEnumFiles(){
        val dir = Path(metaDir, "enums").toFile()
        var listFiles = dir.listFiles { _, name -> name.endsWith(".json") }
        for(file in listFiles){
            val enumJson = FileUtil.readUtf8String(file.absolutePath)
            val enumObject = JSON.parseObject(enumJson)
            val enumName="Enum_${file.nameWithoutExtension}"
            val enumInfo = readEnum(enumObject, enumName)
            buildEnumFile(enumInfo!!, "")
        }
        for(table in tables){
            for(field in table.value.fields){
                if(null==field.enumInfo){
                    continue
                }
                if(field.enumInfo.isGlobal){
                    continue
                }
                buildEnumFile(field.enumInfo, table.value.targetName)
            }
        }
    }
    fun buildEnumFile(enumInfo: EnumInfo, targetName:String){
        val builder = StringBuilder()
        val pkgTargetName=if(targetName.isBlank()) "" else ".${targetName}"
        builder.appendLine("package ${enumsPkg}${pkgTargetName};")
        builder.appendLine()
        builder.appendLine("import com.fmk.framework.basic.IEnum;")
        builder.appendLine("public enum ${enumInfo.name} implements IEnum<${enumInfo.type}> {")
        var i=0
        for(itm in enumInfo.enums){
            builder.appendLine("    /**")
            builder.appendLine("    * ${itm.comment}")
            builder.appendLine("    */")
            var spt=","
            if(i>=enumInfo.enums.size-1){
                spt=";"
            }
            i += 1
            if("String"==enumInfo.type){
                builder.appendLine("    ${itm.name}(\"${itm.value.toString().replace("\"", "\\\"")}\", \"${itm.comment.replace("\"", "\\\"")}\")${spt}")
            }else {
                builder.appendLine("    ${itm.name}(${itm.value}, \"${itm.comment.replace("\"", "\\\"")}\")${spt}")
            }
        }
        builder.appendLine("    private ${enumInfo.type} value;")
        builder.appendLine("    private String title;")
        builder.appendLine("    ${enumInfo.name}(${enumInfo.type} value, String title) {")
        builder.appendLine("        this.value = value;")
        builder.appendLine("        this.title = title;")
        builder.appendLine("    }")
        builder.appendLine("    @Override")
        builder.appendLine("    public ${enumInfo.type} value() {")
        builder.appendLine("        return value;")
        builder.appendLine("    }")
        builder.appendLine("    @Override")
        builder.appendLine("    public String title() {")
        builder.appendLine("        return title;")
        builder.appendLine("    }")
        builder.appendLine("")
        builder.appendLine("}")
        val toFile = Path(enumsDir, targetName, "${enumInfo.name}.java").toFile()
        FileUtil.writeUtf8String(builder.toString(), toFile)
    }
    fun readEnum(jo:JSONObject, name:String): EnumInfo? {
        val value = jo.get("enumItems") ?: return null
        if(value is String){
            val enumFileName = value.substring(5)
            val file = Path(metaDir, "enums", "$enumFileName.json").toFile()
            Preconditions.checkState(file.exists(), "没有找到enum ${enumFileName} 结构定义文件")
            val enumJson = FileUtil.readUtf8String(file.absolutePath)
            val enumObject = JSON.parseObject(enumJson)
            val ja = enumObject.getJSONArray("enumItems")
            val itms=ArrayList<EnumItem>()
            for(itm in ja){
                val eitm = itm as JSONObject
                val value = eitm.get("value")
                val name = eitm.getString("name")
                val comment = eitm.getString("comment")
                itms.add(EnumItem(value!!, name, comment))
            }
            val type = Util.convertJavaType(enumObject.getString("type"))
            val comment = enumObject.getString("comment")
            return EnumInfo(name, type, comment, true, itms)
        }else{
            val ja = value as JSONArray
            val itms=ArrayList<EnumItem>()
            for(itm in ja){
                val eitm = itm as JSONObject
                val value = eitm.get("value")
                val name = eitm.getString("name")
                val comment = eitm.getString("comment")
                itms.add(EnumItem(value!!, name, comment))
            }
            val type = Util.convertJavaType(jo.getString("type"))
            val comment = jo.getString("comment")
            return EnumInfo(name, type, comment, false, itms)
        }
    }
}