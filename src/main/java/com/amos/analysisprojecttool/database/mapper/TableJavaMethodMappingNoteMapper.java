package com.amos.analysisprojecttool.database.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.amos.analysisprojecttool.database.pojo.TableJavaMethodMappingNote;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TableJavaMethodMappingNoteMapper extends BaseMapper<TableJavaMethodMappingNote> {
    int insertOrUpdate(TableJavaMethodMappingNote record);

    int insertList(@Param("list") List<TableJavaMethodMappingNote> list);


}