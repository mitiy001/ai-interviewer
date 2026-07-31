package com.aiinterviewer.dto.resp;

import lombok.Data;

/**
 * 薪资报价（模拟真实公司 offer）
 */
@Data
public class SalaryOfferResp {
    /** 公司类型，如 一线大厂（阿里/腾讯/字节） */
    private String companyType;
    /** Offer 职级，如 P6/P7 */
    private String offerLevel;
    /** 月薪 Base（K） */
    private Integer monthlyBase;
    /** 月薪总包（含绩效，K） */
    private Integer monthlyTotal;
    /** 年薪现金（万） */
    private Integer annualCash;
    /** 股票/期权每年摊销（万） */
    private Integer annualEquity;
    /** 签字费（万） */
    private Integer signOnBonus;
    /** 年薪总包（万） */
    private Integer annualPackage;
    /** 货币单位说明 */
    private String currency;
    /** 报价理由 */
    private String rationale;
}
