package com.example.dianping.entity;


import java.io.Serializable;

import java.time.LocalDateTime;
import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.Length;

/**
*
* @TableName user
*/
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("user")
public class User implements Serializable {

    /**
    * 用户ID
    */
    @TableId(type = IdType.AUTO)
    private Long id;
    /**
    * 手机号
    */
    @NotBlank(message="[手机号]不能为空")
    @Size(max= 11,message="编码长度不能超过11")
    @Length(max= 11,message="编码长度不能超过11")
    private String phone;
    /**
    * 昵称，默认用手机号
    */
    @NotBlank(message="[昵称，默认用手机号]不能为空")
    @Size(max= 32,message="编码长度不能超过32")
    @Length(max= 32,message="编码长度不能超过32")
    private String nickName;
    /**
    * 头像
    */
    @Size(max= 255,message="编码长度不能超过255")
    private String icon;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;

}
