package com.mawten.colony;
import com.google.androidbrowserhelper.playbilling.digitalgoods.DigitalGoodsRequestHandler;
import com.google.androidbrowserhelper.trusted.DelegationService;
public class MawtenDelegationService extends DelegationService {
 @Override public void onCreate(){super.onCreate();registerExtraCommandHandler(new DigitalGoodsRequestHandler(getApplicationContext()));}
}
