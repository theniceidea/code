package com.code.ptool.db

import cn.hutool.core.date.DateTime
import cn.hutool.core.io.FileUtil
import cn.hutool.db.handler.RsHandler
import com.google.common.base.CaseFormat
import java.math.BigDecimal
import java.sql.Timestamp
import kotlin.io.path.Path

class SqlItem(val name:String, val returnType:String, val publish:Boolean, val comment:String, val content:ArrayList<SqlLine>, val mSqlInfo: MSqlInfo) {
    var dbInfo:DbInfo
    init {
        dbInfo = mSqlInfo.project.dbs[mSqlInfo.db]?:throw RuntimeException("sql文件 ${this.mSqlInfo.name} 没有找到db ${mSqlInfo.db} 请检查配置")
    }
    fun buildCode(){
        buildAll()
    }
    private fun buildAll(){
        val firstLine = content[0]
//        if(!firstLine.line.startsWith("select ", true)){
//            throw RuntimeException("sql文件 ${this.sqlInfo.name} sqlItem ${this.name} 第一行语句请以select 开始")
//        }
        val params = findParams()
        val values=HashMap<String, Any>()
        for(s in params){
            if(s.startsWith("int_")){
                values[s]=0
            }else if(s.startsWith("string_")){
                values[s]=""
            }else if(s.startsWith("timestamp_")){
                values[s]= Timestamp(DateTime.now().time)
            }else if(s.startsWith("decimal_")){
                values[s]= BigDecimal.ZERO
            }else if(s.startsWith("double_")){
                values[s]=0
            }else if(s.startsWith("long_")){
                values[s]=0L
            }else if(s.startsWith("list_")){
                values[s]=0L
            }else if(s.startsWith("set_")){
                values[s]=0L
            }else{
                throw throw RuntimeException("sql文件 ${this.mSqlInfo.name} 不支持此类型 ${s}")
            }
        }
        buildReturnModel(dbInfo, values)
        buildSummer()
        buildMsql()
    }
    private fun buildReturnModel(dbInfo: DbInfo, parameters:HashMap<String, Any>){
        val buffer=StringBuilder()
        val gsbuffer=StringBuilder()
        val imports=HashSet<String>()
        buffer.appendLine("package ${mSqlInfo.project.modelPkg}.${mSqlInfo.name};")
        buffer.appendLine("import com.fmk.framework.daoannotations.Column;")
        val className="${this.name}M"

        buffer.appendLine("")
        buffer.appendLine("/**")
        buffer.appendLine("* ${this.comment}")
        buffer.appendLine("*/")
        buffer.appendLine("public class ${className}{")
        val sql = content.joinToString("\n") { it.line.substringBefore("#v ") }
        dbInfo.db.query(sql, RsHandler { rs ->
            val metaData = rs.metaData
            for(i in 1 .. metaData.columnCount){
                val tableName = metaData.getTableName(i)
                val columnName = metaData.getColumnName(i)
                var fcomment=""
                if(tableName.isNotBlank()) {
                    val tableInfo = mSqlInfo.project.tables["${dbInfo.targetName}${tableName}"]
                    if(null != tableInfo){
                        for(field in tableInfo.fields){
                            if(field.name==columnName){
                                fcomment="${tableName}.${field.name} ${tableInfo.comment}.${field.comment}"
                            }
                        }
                    }
                }
                val columnClassName = metaData.getColumnClassName(i)
                if(!columnClassName.startsWith("java.lang")){
                    imports.add(columnClassName)
                }
                val columnLabel = metaData.getColumnLabel(i)
                val _columnName=if(columnLabel.isBlank()) columnName else columnLabel
                val cname= CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, _columnName)
                val cuname="${cname[0].uppercase()}${cname.substring(1)}"
                buffer.appendLine("")
                buffer.appendLine("    /**")
                buffer.appendLine("    * ${fcomment}")
                buffer.appendLine("    */")
                buffer.appendLine("    @Column(\"${_columnName}\")")
                buffer.appendLine("    private ${columnClassName} ${cname};")
                gsbuffer.appendLine("    /**")
                gsbuffer.appendLine("    * ${fcomment}")
                gsbuffer.appendLine("    */")
                gsbuffer.appendLine("    public ${columnClassName} get${cuname}(){")
                gsbuffer.appendLine("        return this.${cname};")
                gsbuffer.appendLine("    }")
                gsbuffer.appendLine("    /**")
                gsbuffer.appendLine("    * ${fcomment}")
                gsbuffer.appendLine("    */")
                gsbuffer.appendLine("    public void set${cuname}(${columnClassName} value){")
                gsbuffer.appendLine("        this.${cname}=value;")
                gsbuffer.appendLine("    }")
            }
        }, parameters)
        buffer.append(gsbuffer)
        buffer.appendLine("}")
        val toFile = Path(mSqlInfo.project.modelDir, mSqlInfo.name, "${className}.java").toFile()
        FileUtil.writeUtf8String(buffer.toString(), toFile)
    }
    private fun buildSummer(){
        val buffer=StringBuilder()
        val gsbuffer=StringBuilder()
        buffer.appendLine("package ${mSqlInfo.project.msqlSummerPkg}.${mSqlInfo.name};")
        buffer.appendLine("import com.fmk.framework.annotations.*;")
        buffer.appendLine("import com.fmk.framework.summer.BasicSummer;")
        buffer.appendLine("import com.fmk.framework.valid.IValidator;")
        buffer.appendLine("import com.fmk.framework.valid.IValidatorSuccess;")
        if(this.returnType=="one") {
        }else if(this.returnType=="list"){
            buffer.appendLine("import java.util.List;")
        }else if(this.returnType=="page"){
            buffer.appendLine("import com.fmk.framework.restful.PageResultList;")
        }

        val className="${this.name}"
        buffer.appendLine("import ${mSqlInfo.project.modelPkg}.${mSqlInfo.name}.${className}M;")
        buffer.appendLine("")
        buffer.appendLine("/**")
        buffer.appendLine("* ${this.comment}")
        buffer.appendLine("*/")
        buffer.appendLine("@ApiInfo(\"${this.comment}\")")
        if(this.publish){
            buffer.appendLine("@Publish")
        }
        val sbuf=StringBuffer()
        val sbuf2=StringBuffer()
        sbuf2.appendLine("        ${className} summer=new ${className}();")
        val params=HashSet<String>()
        var bol=false
        for(line in this.content) {
            if (line.paramName.isBlank()) {
                continue
            }
            val paramName = line.paramName
            if (params.contains(paramName)) {
                continue
            }
            params.add(paramName)
            var typeName = ""
            if (paramName.startsWith("int_")) {
                typeName = "java.lang.Integer"
            } else if (paramName.startsWith("string_")) {
                typeName = "java.lang.String"
            } else if (paramName.startsWith("timestamp_")) {
                typeName = "java.sql.Timestamp"
            } else if (paramName.startsWith("decimal_")) {
                typeName = "java.math.BigDecimal"
            } else if (paramName.startsWith("double_")) {
                typeName = "java.lang.Double"
            } else if (paramName.startsWith("long_")) {
                typeName = "java.lang.Long"
            } else if (paramName.startsWith("list_")) {
                typeName = "java.util.List<Object>"
            } else if (paramName.startsWith("set_")) {
                typeName = "java.util.Set<Object>"
            } else {
                throw throw RuntimeException("sql文件 ${this.mSqlInfo.name} 不支持此类型 ${paramName}")
            }
            val cname = paramName.substringAfter("_", "")
            if(bol){
                sbuf.append(", ")
            }else{
                bol=true
            }
            sbuf.append("${typeName} ${cname}")
            sbuf2.appendLine("        summer.${cname}=${cname};")
        }


        if(this.returnType=="one") {
            buffer.appendLine("public class ${className} extends BasicSummer<${className}M>{")
            buffer.appendLine("    /**")
            buffer.appendLine("    * ${this.comment}")
            buffer.appendLine("    */")
            buffer.append("    public static ${className}M s(")
            buffer.append(sbuf)
            buffer.appendLine(") {")
            buffer.append(sbuf2)
            buffer.appendLine("        return summer.sum();")
            buffer.appendLine("    }")
        }else if(this.returnType=="list"){
            buffer.appendLine("public class ${className} extends BasicSummer<List<${className}M>>{")
            buffer.appendLine("    /**")
            buffer.appendLine("    * ${this.comment}")
            buffer.appendLine("    */")
            buffer.append("    public static List<${className}M> s(")
            buffer.append(sbuf)
            buffer.appendLine(") {")
            buffer.append(sbuf2)
            buffer.appendLine("        return summer.sum();")
            buffer.appendLine("    }")
        }else if(this.returnType=="page"){
            buffer.appendLine("public class ${className} extends BasicSummer<PageResultList<${className}M>>{")
            buffer.appendLine("    /**")
            buffer.appendLine("    * ${this.comment}")
            buffer.appendLine("    */")
            buffer.append("    public static PageResultList<${className}M> s(")
            buffer.append(sbuf)
            buffer.append(", int start, int limit")
            buffer.appendLine(") {")
            buffer.append(sbuf2)
            buffer.appendLine("        summer.start=start;")
            buffer.appendLine("        summer.limit=limit;")
            buffer.appendLine("        return summer.sum();")
            buffer.appendLine("    }")

            buffer.appendLine("    /**")
            buffer.appendLine("    * start 分页查询")
            buffer.appendLine("    */")
            buffer.appendLine("    private int start;")
            buffer.appendLine("    /**")
            buffer.appendLine("    * limit 分页查询")
            buffer.appendLine("    */")
            buffer.appendLine("    private int limit;")
            gsbuffer.appendLine("    /**")
            gsbuffer.appendLine("    * start 分页查询")
            gsbuffer.appendLine("    */")
            gsbuffer.appendLine("    public int getStart(){")
            gsbuffer.appendLine("        return this.start;")
            gsbuffer.appendLine("    }")
            gsbuffer.appendLine("    /**")
            gsbuffer.appendLine("    * start 分页查询")
            gsbuffer.appendLine("    */")
            gsbuffer.appendLine("    public void setStart(int value){")
            gsbuffer.appendLine("        this.start=value;")
            gsbuffer.appendLine("    }")
            gsbuffer.appendLine("    /**")
            gsbuffer.appendLine("    * start 分页查询")
            gsbuffer.appendLine("    */")
            gsbuffer.appendLine("    public ${className} start(int value){")
            gsbuffer.appendLine("        this.start=value;")
            gsbuffer.appendLine("        return this;")
            gsbuffer.appendLine("    }")
            gsbuffer.appendLine("    /**")
            gsbuffer.appendLine("    * limit 分页查询")
            gsbuffer.appendLine("    */")
            gsbuffer.appendLine("    public int getLimit(){")
            gsbuffer.appendLine("        return this.limit;")
            gsbuffer.appendLine("    }")
            gsbuffer.appendLine("    /**")
            gsbuffer.appendLine("    * limit 分页查询")
            gsbuffer.appendLine("    */")
            gsbuffer.appendLine("    public void setLimit(int value){")
            gsbuffer.appendLine("        this.limit=value;")
            gsbuffer.appendLine("    }")
            gsbuffer.appendLine("    /**")
            gsbuffer.appendLine("    * limit 分页查询")
            gsbuffer.appendLine("    */")
            gsbuffer.appendLine("    public ${className} limit(int value){")
            gsbuffer.appendLine("        this.limit=value;")
            gsbuffer.appendLine("        return this;")
            gsbuffer.appendLine("    }")
        }

        buffer.appendLine("    public static ${className} inst(){")
        buffer.appendLine("        return new ${className}();")
        buffer.appendLine("    }")
        params.clear()
        for(line in this.content){
            if(line.paramName.isBlank()){
                continue
            }
            val paramName = line.paramName
            if(params.contains(paramName)){
                continue
            }
            params.add(paramName)
            var typeName=""
            if(paramName.startsWith("int_")){
                typeName="java.lang.Integer"
            }else if(paramName.startsWith("string_")){
                typeName="java.lang.String"
            }else if(paramName.startsWith("timestamp_")){
                typeName="java.sql.Timestamp"
            }else if(paramName.startsWith("decimal_")){
                typeName="java.math.BigDecimal"
            }else if(paramName.startsWith("double_")){
                typeName="java.lang.Double"
            }else if(paramName.startsWith("long_")){
                typeName="java.lang.Long"
            }else if(paramName.startsWith("list_")){
                typeName="java.util.List<Object>"
            }else if(paramName.startsWith("set_")){
                typeName="java.util.Set<Object>"
            }else{
                throw throw RuntimeException("sql文件 ${this.mSqlInfo.name} 不支持此类型 ${paramName}")
            }
            val cname = paramName.substringAfter("_", "")
            val cuname="${cname[0].uppercase()}${cname.substring(1)}"
            val clname="${cname[0].lowercase()}${cname.substring(1)}"

            buffer.appendLine("    /**")
            buffer.appendLine("    * ${line.comment}")
            buffer.appendLine("    */")
            buffer.appendLine("    private ${typeName} $cname;")
            gsbuffer.appendLine("    /**")
            gsbuffer.appendLine("    * ${line.comment}")
            gsbuffer.appendLine("    */")
            gsbuffer.appendLine("    public ${typeName} get${cuname}(){")
            gsbuffer.appendLine("        return this.${cname};")
            gsbuffer.appendLine("    }")
            gsbuffer.appendLine("    /**")
            gsbuffer.appendLine("    * ${line.comment}")
            gsbuffer.appendLine("    */")
            gsbuffer.appendLine("    public void set${cuname}(${typeName} value){")
            gsbuffer.appendLine("        this.${cname}=value;")
            gsbuffer.appendLine("    }")
            gsbuffer.appendLine("    /**")
            gsbuffer.appendLine("    * ${line.comment}")
            gsbuffer.appendLine("    */")
            gsbuffer.appendLine("    public ${className} ${clname}(${typeName} value){")
            gsbuffer.appendLine("        this.${cname}=value;")
            gsbuffer.appendLine("        return this;")
            gsbuffer.appendLine("    }")
            gsbuffer.appendLine("    /**")
            gsbuffer.appendLine("    * ${line.comment}")
            gsbuffer.appendLine("    */")
            gsbuffer.appendLine("    public ${className} ${clname}(${typeName} value, IValidatorSuccess<${typeName}> ... ivs){")
            gsbuffer.appendLine("        if(null != ivs){")
            gsbuffer.appendLine("            for(IValidatorSuccess<${typeName}> itm : ivs){")
            gsbuffer.appendLine("                if(!itm.isValidSuccess(value)){")
            gsbuffer.appendLine("                    return this;")
            gsbuffer.appendLine("                }")
            gsbuffer.appendLine("            }")
            gsbuffer.appendLine("        }")
            gsbuffer.appendLine("")
            gsbuffer.appendLine("        this.${cname}=value;")
            gsbuffer.appendLine("        return this;")
            gsbuffer.appendLine("    }")

            gsbuffer.appendLine("    /**")
            gsbuffer.appendLine("    * ${line.comment}")
            gsbuffer.appendLine("    */")
            gsbuffer.appendLine("    public ${className} ${clname}_valid(IValidator<${typeName}> validator, String msg){")
            gsbuffer.appendLine("        validator.valid(this.${cname}, msg);")
            gsbuffer.appendLine("        return this;")
            gsbuffer.appendLine("    }")
        }
        buffer.append(gsbuffer)
        buffer.appendLine("}")
        val toFile = Path(mSqlInfo.project.msqlSummerDir, mSqlInfo.name, "${className}.java").toFile()
        FileUtil.writeUtf8String(buffer.toString(), toFile)
    }
    private fun buildMsql(){
        val buffer=StringBuilder()
        val gsbuffer=StringBuilder()
        buffer.appendLine("package ${mSqlInfo.project.msqlPkg}.${mSqlInfo.name};")
//        buffer.appendLine(mSqlInfo.imps)
        buffer.appendLine("import ${mSqlInfo.project.msqlSummerPkg}.${mSqlInfo.name}.${this.name};")
        buffer.appendLine("import org.springframework.stereotype.Service;")
        buffer.appendLine("import org.summerframework.model.SummerService;")
        buffer.appendLine("import com.fmk.framework.validation.Precondition;")
        buffer.appendLine("import com.fmk.framework.basic.validat.SqlValidator;")
        buffer.appendLine("import java.util.Collection;")
        buffer.appendLine("import com.fmk.framework.daomodel.*;")
        buffer.appendLine("import com.fmk.framework.daosimple.BQuerySelect;")
        buffer.appendLine("import org.summerframework.model.SummerServiceBean;")
        buffer.appendLine("import org.apache.commons.lang3.StringUtils;")
        buffer.appendLine("import com.${mSqlInfo.project.comp}.model0.bizdemo.${mSqlInfo.name}.*;")
        buffer.appendLine("import java.util.List;")
        if(this.returnType=="one") {
        }else if(this.returnType=="list"){
        }else if(this.returnType=="page"){
            buffer.appendLine("import com.fmk.framework.restful.PageResultList;")
        }
        buffer.appendLine("")
        val className="${this.name}Service"
        buffer.appendLine("")
        buffer.appendLine("/**")
        buffer.appendLine("* ${this.comment}")
        buffer.appendLine("*/")

        buffer.appendLine("@Service")
        buffer.appendLine("@SummerService")
        buffer.appendLine("public class ${className} implements SummerServiceBean<${this.name}> {")
//        buffer.appendLine("    private static Object DS=null;")
//        buffer.appendLine("    private static boolean DS_INITED=false;")
        buffer.appendLine("    private static Object DB_TARGET=null;")
        buffer.appendLine("    private Object instDbTarget__ =null;")
        buffer.appendLine("    private Object ds(){")
        buffer.appendLine("        if(null != instDbTarget__){")
        buffer.appendLine("            return instDbTarget__;")
        buffer.appendLine("        }")
        buffer.appendLine("")
        buffer.appendLine("        if(null != DB_TARGET){")
        buffer.appendLine("            return DB_TARGET;")
        buffer.appendLine("        }")
        val dbInfo = mSqlInfo.project.dbs[mSqlInfo.db]?:throw RuntimeException("sql文件 ${this.mSqlInfo.name} 没有找到db ${mSqlInfo.db} 请检查配置")
        buffer.appendLine("        DB_TARGET=DaoJdbcTemplate.s(\"${dbInfo.targetName}\");")
        buffer.appendLine("        return DB_TARGET;")
        buffer.appendLine("    }")
        buffer.appendLine("")
        buffer.appendLine("    @Override")
        buffer.appendLine("    public void sum(${this.name} summer) {")
        buffer.appendLine("        final BQuerySelect bqs = new BQuerySelect();")
        buffer.appendLine("        final StringBuilder builder = bqs.getBuilder();")
        buffer.appendLine("        final List<Object> values = bqs.getParameters();")
        buffer.appendLine("        boolean bol=true;")
        var ci=0
        for(itm in this.content) {
            val sline = itm.line.substringBefore("#v ").trimEnd().trimEnd(';')
            if(ci==0){
                buffer.appendLine("        bqs.setSelect(\"${sline}\");")
                ci += 1
                continue
            }
            if(itm.paramName.isBlank()) {
                buffer.appendLine("        builder.append(\" ${sline}\");")
            }else{
                buffer.appendLine("")
                buffer.appendLine("        bol=true;")
                val cname = itm.paramName.substringAfter("_", "")
                val cuname="${cname[0].uppercase()}${cname.substring(1)}"
                if(itm.errMsg.isBlank()) {
                    for (chk in itm.checkFuns) {
                        buffer.appendLine("        bol=bol && SqlValidator.${chk}(summer.get${cuname}());")
                    }
                    buffer.appendLine("        if(bol){")
                    if(itm.paramName.startsWith("list_") || itm.paramName.startsWith("set_")){
                        buffer.appendLine("            String txt = StringUtils.repeat(\"?\", \",\", summer.get${cuname}().size());")
                        buffer.appendLine("            builder.append(\" ${sline.replace("\"", "\\\"")}\".replace(\":${itm.paramName}\", txt));")
                        buffer.appendLine("            values.addAll(summer.get${cuname}());")
                        buffer.appendLine("            ")
                    }else{
                        buffer.appendLine("            builder.append(\" ${sline.replace("\"", "\\\"").replace(":${itm.paramName}", "?")}\");")
                        buffer.appendLine("            values.add(summer.get${cuname}());")
                    }
                    buffer.appendLine("        }")
                }else{
                    for (chk in itm.checkFuns) {
                        buffer.appendLine("        bol=bol && SqlValidator.${chk}(summer.get${cuname}());")
                    }
                    buffer.appendLine("        Precondition.checkState(bol, \"${itm.errMsg.substring(4).replace("\"", "\\\"")}\");")
                    if(itm.paramName.startsWith("list_") || itm.paramName.startsWith("set_")){
                        buffer.appendLine("        String txt = StringUtils.repeat(\"?\", \",\", summer.get${cuname}().size());")
                        buffer.appendLine("        builder.append(\" ${sline.replace("\"", "\\\"")}\".replace(\":${itm.paramName}\", txt));")
                        buffer.appendLine("        values.addAll(summer.get${cuname}());")
                        buffer.appendLine("        ")
                    }else{
                        buffer.appendLine("        builder.append(\" ${sline.replace("\"", "\\\"").replace(":${itm.paramName}", "?")}\");")
                        buffer.appendLine("        values.add(summer.get${cuname}());")
                    }
                }
            }
        }
        buffer.appendLine("")
        val mclassName="${this.name}M"
        if(this.returnType=="one") {
            buffer.appendLine("        final List<${mclassName}> list = DaoList.s(ds(), ${mclassName}.class, bqs, 0, 1);")
            buffer.appendLine("        if(list.size()>0){")
            buffer.appendLine("            summer.setSummerResult(list.get(0));")
            buffer.appendLine("        }")
        }else if(this.returnType=="list"){
            buffer.appendLine("        final List<${mclassName}> list = DaoList.s(ds(), ${mclassName}.class, bqs);")
            buffer.appendLine("        summer.setSummerResult(list);")
        }else if(this.returnType=="page"){
            buffer.appendLine("        final PageResultList<${mclassName}> list = DaoPageList.s(ds(), ${mclassName}.class, bqs, summer.getStart(), summer.getLimit());")
            buffer.appendLine("        summer.setSummerResult(list);")
        }
        buffer.appendLine("")
        buffer.appendLine("    }")
        buffer.appendLine("}")
        buffer.appendLine("")
        val toFile = Path(mSqlInfo.project.msqlDir, mSqlInfo.name, "${className}.java").toFile()
        FileUtil.writeUtf8String(buffer.toString(), toFile)
    }
    private fun findParams():HashSet<String>{
        val params=HashSet<String>()
        for(i in 0 until content.size){
            val codeLine = content[i]
            if(codeLine.paramName.isBlank()){
                continue
            }
            params.add(codeLine.paramName)
        }
        return params
    }
}