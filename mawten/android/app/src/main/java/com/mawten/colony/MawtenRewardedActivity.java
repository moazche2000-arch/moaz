package com.mawten.colony;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.androidbrowserhelper.trusted.LauncherActivity;
import com.google.android.gms.ads.*;
import com.google.android.gms.ads.rewarded.*;
import com.google.android.ump.*;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Native rewarded-only flow. Client callbacks never credit production. Google SSV is authoritative. */
public final class MawtenRewardedActivity extends Activity {
 private static final String BASE="https://mawten-wadi-colony.moazkhoulani.workers.dev";
 private static final AtomicBoolean initialized=new AtomicBoolean(false);
 private final ExecutorService network=Executors.newSingleThreadExecutor();
 private TextView status;private String ticket="",unit="",userId="";private boolean test=false,earned=false,returned=false,loading=false;private RewardedAd ad;
 @Override public void onCreate(Bundle saved){super.onCreate(saved);buildUi();Uri uri=getIntent().getData();if(uri==null||!"mawten".equals(uri.getScheme())){message("رابط الإعلان غير صالح.");return;}String mode=uri.getHost();if("privacy".equals(mode)){privacy();return;}if("rewarded-test".equals(mode)){test=true;unit="ca-app-pub-3940256099942544/5224354917";if(BuildConfig.ADMOB_CONFIGURED){message("نسخة الإنتاج لا تعرض إعلان اختبار عبر هذا الزر.");return;}message("إعلان Google تجريبي؛ لا يربح أموالًا ولا يمنح مكافأة حقيقية.");consent();return;}if(!"rewarded".equals(mode)){message("طلب غير صالح.");return;}ticket=uri.getQueryParameter("ticket");if(ticket==null||!ticket.matches("[a-f0-9]{48}")){ticket="";message("طلب إعلان غير صالح.");return;}if(!BuildConfig.ADMOB_CONFIGURED){message("هذه نسخة تجهيز. يلزم ربط حساب AdMob وتثبيت نسخة الإنتاج لتشغيل المكافآت.");return;}if(saved!=null){message("عد إلى اللعبة للتحقق من حالة الإعلان قبل إعادة المحاولة.");return;}loading=true;message("جارٍ تجهيز الإعلان الاختياري…");network.execute(()->{try{JSONObject data=post("/api/ads/native-start",new JSONObject().put("ticket",ticket));if(!getString(R.string.admob_app_id).equals(data.getString("appId")))throw new Exception("نسخة التطبيق لا تطابق إعداد حساب الإعلانات؛ حدّث التطبيق.");unit=data.getString("adUnit");userId=data.getString("userId");if(!unit.matches("ca-app-pub-[0-9]{16}/[0-9]{10}")||!ticket.equals(data.getString("customData"))||!userId.matches("[a-f0-9]{64}"))throw new Exception("تعذر توثيق إعداد الإعلان.");runOnUiThread(this::consent);}catch(Exception e){runOnUiThread(()->failed(e.getMessage()));}});}
 private void buildUi(){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setGravity(Gravity.CENTER);box.setPadding(28,32,28,32);box.setBackgroundColor(Color.rgb(18,54,59));TextView title=new TextView(this);title.setText("موطن · مكافأة الإنتاج");title.setTextColor(Color.rgb(235,203,141));title.setTextSize(24);title.setGravity(Gravity.CENTER);box.addView(title);status=new TextView(this);status.setTextColor(Color.rgb(243,232,203));status.setTextSize(17);status.setGravity(Gravity.CENTER);status.setPadding(8,24,8,24);box.addView(status);Button back=new Button(this);back.setText("العودة إلى اللعبة");back.setOnClickListener(v->leave());box.addView(back);setContentView(box);}
 private void message(String text){if(!isFinishing()&&!isDestroyed())status.setText(text==null?"تعذر عرض الإعلان الآن؛ يمكنك اللعب دون إعلان.":text);}
 private void consent(){if(isFinishing()||isDestroyed())return;ConsentInformation info=UserMessagingPlatform.getConsentInformation(this);info.requestConsentInfoUpdate(this,new ConsentRequestParameters.Builder().build(),()->UserMessagingPlatform.loadAndShowConsentFormIfRequired(this,error->{if(isFinishing()||isDestroyed())return;if(info.canRequestAds())initializeAndLoad();else failed("لم يُسمح بطلب الإعلان. يمكنك متابعة اللعب دون مشاهدة إعلان.");}),error->{if(info.canRequestAds())initializeAndLoad();else failed("تعذر التحقق من خيارات الخصوصية. حاول لاحقًا.");});}
 private void initializeAndLoad(){if(isFinishing()||isDestroyed())return;MobileAds.setRequestConfiguration(new RequestConfiguration.Builder().setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_G).build());if(initialized.compareAndSet(false,true))network.execute(()->MobileAds.initialize(this,result->runOnUiThread(this::load)));else load();}
 private void load(){if(isFinishing()||isDestroyed())return;Bundle extras=new Bundle();extras.putString("npa","1");AdRequest request=new AdRequest.Builder().addNetworkExtrasBundle(com.google.ads.mediation.admob.AdMobAdapter.class,extras).build();RewardedAd.load(this,unit,request,new RewardedAdLoadCallback(){@Override public void onAdFailedToLoad(LoadAdError error){failed("لا يوجد إعلان متاح الآن. لم تُستهلك منك أي موارد؛ حاول لاحقًا.");}@Override public void onAdLoaded(RewardedAd loaded){if(isFinishing()||isDestroyed())return;ad=loaded;if(!test)ad.setServerSideVerificationOptions(new ServerSideVerificationOptions.Builder().setCustomData(ticket).setUserId(userId).build());ad.setFullScreenContentCallback(new FullScreenContentCallback(){@Override public void onAdFailedToShowFullScreenContent(AdError error){failed("تعذر تشغيل الإعلان؛ لم تُمنح مكافأة غير مؤكدة.");}@Override public void onAdDismissedFullScreenContent(){finishHint(earned?"earned":"cancelled");leave();}});loading=false;ad.show(MawtenRewardedActivity.this,reward->{earned=true;finishHint("earned");});}});}
 private void privacy(){ConsentInformation info=UserMessagingPlatform.getConsentInformation(this);info.requestConsentInfoUpdate(this,new ConsentRequestParameters.Builder().build(),()->{if(info.getPrivacyOptionsRequirementStatus()==ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED)UserMessagingPlatform.showPrivacyOptionsForm(this,error->message(error==null?"حُفظت خيارات الخصوصية.":"تعذر عرض الخيارات الآن."));else message("لا توجد خيارات موافقة مطلوبة حاليًا من خدمة الإعلانات.");},error->message("تعذر تحميل خيارات الخصوصية الآن."));}
 private void failed(String text){loading=false;finishHint("failed");message(text);}
 private void finishHint(String result){if(test||ticket.isEmpty()||network.isShutdown())return;network.execute(()->{try{post("/api/ads/native-finish",new JSONObject().put("ticket",ticket).put("result",result));}catch(Exception ignored){/* No reward is credited here; signed SSV may still arrive. */}});}
 private JSONObject post(String path,JSONObject body)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(BASE+path).openConnection();try{c.setConnectTimeout(7000);c.setReadTimeout(7000);c.setInstanceFollowRedirects(false);c.setRequestMethod("POST");c.setRequestProperty("Origin",BASE);c.setRequestProperty("Content-Type","application/json");c.setDoOutput(true);try(java.io.OutputStream output=c.getOutputStream()){output.write(body.toString().getBytes(StandardCharsets.UTF_8));}int code=c.getResponseCode();InputStream input=code>=200&&code<300?c.getInputStream():c.getErrorStream();ByteArrayOutputStream bytes=new ByteArrayOutputStream();if(input!=null)try(InputStream in=input){byte[] chunk=new byte[2048];int n;while((n=in.read(chunk))!=-1){if(bytes.size()+n>16384)throw new Exception("استجابة غير صالحة.");bytes.write(chunk,0,n);}}JSONObject data=new JSONObject(bytes.toString("UTF-8"));if(code<200||code>=300)throw new Exception(data.optString("error","تعذر الاتصال بخدمة الإعلان."));return data;}finally{c.disconnect();}}
 private void leave(){if(returned)return;returned=true;if(loading)finishHint("failed");String suffix=test?"?ad_test=done":ticket.isEmpty()?"":"?ad_ticket="+Uri.encode(ticket);Intent back=new Intent(this,LauncherActivity.class);back.setAction(Intent.ACTION_VIEW);back.setData(Uri.parse(BASE+"/"+suffix));back.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);startActivity(back);finish();}
 @Override public void onBackPressed(){finishHint(earned?"earned":"cancelled");leave();}
 @Override protected void onDestroy(){ad=null;network.shutdown();super.onDestroy();}
}
