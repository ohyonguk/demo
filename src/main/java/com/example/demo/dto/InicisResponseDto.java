package com.example.demo.dto;

public class InicisResponseDto {
    
    private String resultCode;
    private String resultMsg;
    private String mid;
    private String oid;
    private String price;
    private String timestamp;
    private String signature;
    private String mKey;
    private String tid;
    private String applDate;
    private String applTime;
    private String applNum;
    private String cardCode;
    private String cardName;
    private String cardQuota;
    private String cardInterest;
    private String cardCheckflag;
    private String cardPoint;
    private String cardBankCode;
    private String cardBankName;
    private String cardCloseDate;
    
    public InicisResponseDto() {}
    
    public String getResultCode() { return resultCode; }
    public void setResultCode(String resultCode) { this.resultCode = resultCode; }
    
    public String getResultMsg() { return resultMsg; }
    public void setResultMsg(String resultMsg) { this.resultMsg = resultMsg; }
    
    public String getMid() { return mid; }
    public void setMid(String mid) { this.mid = mid; }
    
    public String getOid() { return oid; }
    public void setOid(String oid) { this.oid = oid; }
    
    public String getPrice() { return price; }
    public void setPrice(String price) { this.price = price; }
    
    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
    
    public String getSignature() { return signature; }
    public void setSignature(String signature) { this.signature = signature; }
    
    public String getmKey() { return mKey; }
    public void setmKey(String mKey) { this.mKey = mKey; }
    
    public String getTid() { return tid; }
    public void setTid(String tid) { this.tid = tid; }
    
    public String getApplDate() { return applDate; }
    public void setApplDate(String applDate) { this.applDate = applDate; }
    
    public String getApplTime() { return applTime; }
    public void setApplTime(String applTime) { this.applTime = applTime; }
    
    public String getApplNum() { return applNum; }
    public void setApplNum(String applNum) { this.applNum = applNum; }
    
    public String getCardCode() { return cardCode; }
    public void setCardCode(String cardCode) { this.cardCode = cardCode; }
    
    public String getCardName() { return cardName; }
    public void setCardName(String cardName) { this.cardName = cardName; }
    
    public String getCardQuota() { return cardQuota; }
    public void setCardQuota(String cardQuota) { this.cardQuota = cardQuota; }
    
    public String getCardInterest() { return cardInterest; }
    public void setCardInterest(String cardInterest) { this.cardInterest = cardInterest; }
    
    public String getCardCheckflag() { return cardCheckflag; }
    public void setCardCheckflag(String cardCheckflag) { this.cardCheckflag = cardCheckflag; }
    
    public String getCardPoint() { return cardPoint; }
    public void setCardPoint(String cardPoint) { this.cardPoint = cardPoint; }
    
    public String getCardBankCode() { return cardBankCode; }
    public void setCardBankCode(String cardBankCode) { this.cardBankCode = cardBankCode; }
    
    public String getCardBankName() { return cardBankName; }
    public void setCardBankName(String cardBankName) { this.cardBankName = cardBankName; }
    
    public String getCardCloseDate() { return cardCloseDate; }
    public void setCardCloseDate(String cardCloseDate) { this.cardCloseDate = cardCloseDate; }
    
    public boolean isSuccess() {
        return "0000".equals(resultCode);
    }
}