package com.code.ptool.db

import cn.hutool.db.Db
import com.alibaba.druid.pool.DruidDataSource
import java.sql.Driver


class DbInfo(val targetName:String, val url:String, val driver:String, val user:String, val pwd:String) {
    var db:Db
    init {
        val ds2 = DruidDataSource()
        ds2.url = url
        ds2.username = user
        ds2.password = pwd
        ds2.driver=Class.forName(driver).newInstance() as Driver
        db= Db.use(ds2)
    }
}