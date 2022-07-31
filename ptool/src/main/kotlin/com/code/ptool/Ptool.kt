package com.code.ptool

import cn.hutool.core.io.FileUtil
import cn.hutool.core.util.StrUtil
import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONArray
import com.alibaba.fastjson.JSONObject
import com.code.ptool.db.DbInfo
import com.google.common.base.Preconditions
import java.io.File
import kotlin.io.path.Path

class Ptool(val config: JSONObject) {
    val projs=HashMap<String, Project>()
    init {
        initProjects()
    }
    fun initProjects(){
        var projectsja = config.getJSONArray("projects")
        for(proj in projectsja){
            if(proj !is JSONObject){
                continue
            }

            val name = proj.getString("name")
            Preconditions.checkState(StrUtil.isNotBlank(name), "需要有projects.name")
            val idTag = proj.getString("idTag").orEmpty()
//            Preconditions.checkState(StrUtil.isNotBlank(idTag), "需要有projects.idTag")
            val comp = proj.getString("comp")
            Preconditions.checkState(StrUtil.isNotBlank(comp), "需要有projects.comp")
            val baseDir = proj.getString("baseDir")
            Preconditions.checkState(StrUtil.isNotBlank(baseDir), "需要有projects.baseDir")

            val metaDir = Path(baseDir, name, "meta").toFile().absolutePath
            val entityPkg = "com.${comp}.entities0.${name}"
            val entityDir = Path(baseDir, name, "${name}-model/src/main/java/com/${comp}/entities0/${name}").toFile().absolutePath

            val enumsPkg = "com.${comp}.enums0.${name}"
            val enumsDir = Path(baseDir, name, "${name}-model/src/main/java/com/${comp}/enums0/${name}").toFile().absolutePath

            val modelPkg = "com.${comp}.model0.${name}"
            val modelDir = Path(baseDir, name, "${name}-model/src/main/java/com/${comp}/model0/${name}").toFile().absolutePath

            val queryPkg = "com.${comp}.query0.${name}"
            val queryDir = Path(baseDir, name, "${name}-boot/src/main/java/com/${comp}/query0/${name}").toFile().absolutePath

            val msqlPkg = "com.${comp}.msql0.${name}"
            val msqlDir = Path(baseDir, name, "${name}-boot/src/main/java/com/${comp}/msql0/${name}").toFile().absolutePath

            val msqlSummerPkg = "com.${comp}.summer.v0.service.${name}"
            val msqlSummerDir = Path(baseDir, name, "${name}-model/src/main/java/com/${comp}/summer/v0/service/${name}").toFile().absolutePath

            Preconditions.checkState(proj.containsKey("tables"), "需要有projects.tables")
            Preconditions.checkState(proj["tables"] is JSONArray, "projects.tables 需要是array")

            val project = Project(name, comp, idTag, metaDir, entityPkg, entityDir, enumsPkg, enumsDir, queryPkg, queryDir, msqlPkg, msqlDir, msqlSummerPkg, msqlSummerDir, modelPkg, modelDir)

            val dbsJson = proj.getJSONObject("dbs")
            if(null != dbsJson){
                for(dbi in dbsJson){
                    val dbiv = dbi.value
                    if(dbiv !is JSONObject){
                        continue
                    }
                    val targetName = dbiv.getString("targetName").orEmpty()
//                    Preconditions.checkState(StrUtil.isNotBlank(targetName), "请检查application.json db ${dbi.key} 需要有 targetName")
                    val dburl = dbiv.getString("url")
                    Preconditions.checkState(StrUtil.isNotBlank(dburl), "请检查application.json db ${dbi.key} 需要有 url")
                    val dbdriver = dbiv.getString("driver")
                    Preconditions.checkState(StrUtil.isNotBlank(dbdriver), "请检查application.json db ${dbi.key} 需要有 driver")
                    val dbuser = dbiv.getString("user")
                    Preconditions.checkState(StrUtil.isNotBlank(dbuser), "请检查application.json db ${dbi.key} 需要有 user")
                    val dbpwd = dbiv.getString("pwd")
//                    Preconditions.checkState(StrUtil.isNotBlank(dbpwd), "请检查application.json db ${dbi.key} 需要有 pwd")
                    project.dbs[dbi.key]= DbInfo(targetName, dburl, dbdriver, dbuser, dbpwd)
                }
            }
            projs[name]=project
            val jaTables = proj.getJSONArray("tables")
            project.initTables(jaTables)
            project.initSqls()
        }
    }
    fun build(){
        for(proj in projs.values){
            proj.build()
        }
    }
}
fun main(args: Array<String>) {
    val config= readConfig("application.json")
    val ptool = Ptool(config)
    ptool.build()
//    val list = Db.use().query("show full columns from room_rentorder;")
//    val dataSource = DSFactory.get()
//    val columnNames = MetaUtil.getColumnNames(dataSource, "room_rentorder")
    println("")
//    println("====:${config.vs("name", false)}")
}
private fun readConfig(fileName:String): JSONObject {
    val file = File(fileName)
    if(!file.exists()){
        throw RuntimeException("缺少配置文件 ${file.absolutePath}")
    }
    return JSON.parseObject(FileUtil.readUtf8String(file))
}
