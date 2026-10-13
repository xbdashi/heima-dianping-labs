package com.example.dianping.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Shop {
    @TableId(type = IdType.AUTO)
    private Long id;
    @NotBlank(message = "店铺名称不能为空") // notnull是不能为空，但可以为空字符，
    @Size(max = 100,message = "店铺名称不能超过100个字符") // 非数字类型判断使用size
    private String name;
    @Positive(message = "类型ID不能为负数") // 必须大于0，允许空
    private Long typeId;
    private String address;
    @Min(value = 0,message = "平均价格不能为负数") // 必须大于等于0，允许空和null
    private Integer avgPrice;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
}
