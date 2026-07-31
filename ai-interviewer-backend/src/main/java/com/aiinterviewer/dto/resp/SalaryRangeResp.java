package com.aiinterviewer.dto.resp;

import lombok.Data;

/**
 * 薪资范围估算
 */
@Data
public class SalaryRangeResp {
    /** 职级，如 P7/高级工程师 */
    private String level;
    /** 月薪下限（K） */
    private Integer monthlyMin;
    /** 月薪上限（K） */
    private Integer monthlyMax;
    /** 年薪下限（万） */
    private Integer annualMin;
    /** 年薪上限（万） */
    private Integer annualMax;
    /** 货币单位说明 */
    private String currency;
    /** 备注 */
    private String note;
}
