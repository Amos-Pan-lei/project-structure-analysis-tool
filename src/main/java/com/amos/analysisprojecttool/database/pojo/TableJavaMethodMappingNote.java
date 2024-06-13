package com.amos.analysisprojecttool.database.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 表与方法对应关系表
 */
@Data
@ApiModel(description = "表与方法对应关系表")
@TableName(value = "table_java_method_mapping_note")
public class TableJavaMethodMappingNote {
    @TableId(value = "id", type = IdType.INPUT)
    @ApiModelProperty(value = "")
    private Integer id;

    /**
     * 表名称
     */
    @TableField(value = "`table_name`")
    @ApiModelProperty(value = "表名称")
    private String tableName;

    /**
     * 所属模块
     */
    @TableField(value = "module_name")
    @ApiModelProperty(value = "所属模块")
    private String moduleName;

    /**
     * 类名
     */
    @TableField(value = "class_name")
    @ApiModelProperty(value = "类名")
    private String className;

    /**
     * 方法名称
     */
    @TableField(value = "method_name")
    @ApiModelProperty(value = "方法名称")
    private String methodName;


    /**
     * 方法签名
     */
    @TableField(value = "method_signature")
    @ApiModelProperty(value = "方法签名")
    private String methodSignature;

    /**
     * 备注
     */
    @TableField(value = "remark")
    @ApiModelProperty(value = "备注")
    private String remark;

}