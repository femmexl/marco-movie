// Copyright (C) 2026 Pilipala. SPDX-License-Identifier: GPL-3.0-only
package com.pilipala.marcomovie.lite;

import android.app.Activity;
import android.os.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;
import com.google.zxing.*;
import com.google.zxing.common.BitMatrix;

/** Android 4.0+ (API 14+) Lite Edition: configuration -> CMS -> detail -> playback with touch & D-pad controls. */
public final class LiteActivity extends Activity {
 final Handler ui=new Handler();
 final ThreadPoolExecutor io=new ThreadPoolExecutor(2,2,0L,TimeUnit.MILLISECONDS,new LinkedBlockingQueue<Runnable>(8),new ThreadPoolExecutor.DiscardOldestPolicy());
 Future<?> activeWork;
 LinearLayout page;TextView status;ConfigServer pairing;LitePlayer player;SurfaceView surface;FrameLayout playback;
 java.util.List<LiteCatalog.Site> sites=new ArrayList<LiteCatalog.Site>();LiteCatalog.Site selected;
 java.util.List<LiteCatalog.Category> categories=new ArrayList<LiteCatalog.Category>();
 volatile int epoch;volatile boolean ready,dead;JSONObject currentDetail;final Object importLock=new Object();boolean homeVisible,inPlayback,backgrounded;
 SeekBar timeline;TextView clockView;boolean touchSeeking,sliderSeeking;int lastUserSeekMs=-1;long lastUserSeekAt;
 final Runnable clockTick=new Runnable(){
  public void run(){
   if(dead||!inPlayback||player==null)return;
   if(ready&&!touchSeeking&&!sliderSeeking){
    try{
     int pos=player.getCurrentPosition(),dur=player.getDuration();
     if(lastUserSeekMs>=0&&SystemClock.uptimeMillis()-lastUserSeekAt<1500)pos=lastUserSeekMs;
     else lastUserSeekMs=-1;
     if(timeline!=null&&dur>0){timeline.setMax(dur);timeline.setProgress(Math.max(0,Math.min(dur,pos)));}
     if(clockView!=null)clockView.setText(formatTime(pos)+" / "+formatTime(dur));
    }catch(Exception ignored){}
   }
   ui.postDelayed(this,1000);
  }
 };
 interface Job {void run()throws Exception;}
 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  String crashedSite=getPreferences(0).getString("loading_site","");
  if(crashedSite!=null&&crashedSite.length()>0){
   getPreferences(0).edit().putString("loading_site","").putString("selected_site","").commit();
  }
  home();
  if(crashedSite!=null&&crashedSite.length()>0&&status!=null){
   status.setText("上次加载片源时异常退出，已自动重置片源选择。");
  }
 }
 int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
 static String formatTime(int ms){
  int total=Math.max(0,ms/1000),s=total%60,m=(total/60)%60,h=total/3600;
  return h>0?String.format(Locale.US,"%d:%02d:%02d",h,m,s):String.format(Locale.US,"%02d:%02d",m,s);
 }
 TextView text(String value,int size){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(0xffeeeeF2);t.setPadding(dp(8),dp(8),dp(8),dp(8));return t;}
 void cancelWork(){if(activeWork!=null){activeWork.cancel(true);activeWork=null;}io.purge();LiteCatalog.cancelAll();}
 void base(String title){homeVisible=false;inPlayback=false;ui.removeCallbacks(clockTick);epoch++;cancelWork();closePairing();release();ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(0xff101013);page=new LinearLayout(this);page.setOrientation(1);page.setPadding(dp(24),dp(16),dp(24),dp(24));scroll.addView(page);setContentView(scroll);page.addView(text(title,26));status=text("",15);page.addView(status);}
 void button(String title,final Runnable action){final TextView b=text(title,18);b.setGravity(Gravity.CENTER);b.setFocusable(true);b.setClickable(true);style(b,false);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(52));lp.setMargins(dp(4),dp(4),dp(4),dp(4));page.addView(b,lp);b.setOnFocusChangeListener(new View.OnFocusChangeListener(){public void onFocusChange(View v,boolean focus){style(b,focus);}});b.setOnClickListener(new View.OnClickListener(){public void onClick(View v){action.run();}});}
 void style(TextView b,boolean focused){GradientDrawable bg=new GradientDrawable();bg.setColor(focused?0xffeeeeF2:0xff28282f);bg.setCornerRadius(dp(14));b.setBackgroundDrawable(bg);b.setTextColor(focused?0xff101013:0xffeeeeF2);}
 void work(final Job job){final int id=epoch;status.setText("正在加载…");cancelWork();activeWork=io.submit(new Runnable(){public void run(){if(dead||epoch!=id)return;try{job.run();}catch(final Throwable ex){ui.post(new Runnable(){public void run(){getPreferences(0).edit().putString("loading_site","").commit();if(!dead&&epoch==id)status.setText("加载失败，请重试。请检查网络、配置类型及旧系统 HTTPS 兼容性。");}});}}});}
 void home(){
  base("MARCO MOVIE  Lite");homeVisible=true;
  if(PlayerFactory.hasCandidate())button(getPreferences(0).getBoolean("experimental_decoder",false)?"解码：IJK 实验版":"解码：系统",new Runnable(){public void run(){getPreferences(0).edit().putBoolean("experimental_decoder",!getPreferences(0).getBoolean("experimental_decoder",false)).commit();home();}});
  status.setText("Android 4.0+ 轻量兼容版 · 支持老旧电视与手机 · 不含任何影视资源");
  button("手机扫码配置",new Runnable(){public void run(){configure();}});
  button("输入配置地址",new Runnable(){public void run(){importUrlPage();}});
  String raw=getPreferences(0).getString("config","");
  try{if(raw.length()>0)sites=LiteCatalog.parse(raw,getPreferences(0).getString("address",""));}catch(Throwable ignored){}
  for(final LiteCatalog.Site s:sites)button(s.name+(s.supported?"":"  · 尚未兼容"),new Runnable(){public void run(){if(s.supported){selected=s;categories.clear();getPreferences(0).edit().putString("selected_site",s.key).putString("loading_site",s.key).commit();catalog("",1);}else status.setText("此来源需要插件、JS 或网页解析，Android 4.0 轻量版仅支持标准 JSON CMS 片源。");}});
 }
 void importUrlPage(){
  base("输入配置地址");
  status.setText("请输入合法获取的 HTTP(S) JSON 配置地址：");
  final EditText input=new EditText(this);
  input.setSingleLine();
  input.setTextColor(0xffffffff);
  input.setHint("https://…/config.json");
  input.setText(getPreferences(0).getString("address",""));
  page.addView(input);
  button("导入并保存配置",new Runnable(){
   public void run(){
    final String addr=input.getText().toString().trim();
    if(addr.length()==0){status.setText("请输入有效的 HTTP(S) 配置地址");return;}
    work(new Job(){
     public void run()throws Exception{
      final String normalized=LiteCatalog.http(addr);
      final String raw=LiteCatalog.fetch(normalized,null);
      LiteCatalog.parse(raw,normalized);
      synchronized(importLock){
       getPreferences(0).edit().putString("address",normalized).putString("config",raw).commit();
      }
      ui.post(new Runnable(){public void run(){if(!dead)home();}});
     }
    });
   }
  });
  button("返回片库",new Runnable(){public void run(){home();}});
 }
 void configure(){base("手机配置");final int session=epoch;status.setText("手机与电视连接同一局域网；配置只保存在本机。");try{
  pairing=new ConfigServer(new ConfigServer.Importer(){public void apply(String address)throws Exception {String raw=LiteCatalog.fetch(address,null);LiteCatalog.parse(raw,address);synchronized(importLock){if(dead||pairing==null||epoch!=session)throw new java.io.IOException();if(!getPreferences(0).edit().putString("address",address).putString("config",raw).commit())throw new java.io.IOException();}ui.post(new Runnable(){public void run(){if(pairing!=null)status.setText("配置已保存，点击“返回片库”选择片源。");}});}});
  String host=null;Enumeration<NetworkInterface> nets=NetworkInterface.getNetworkInterfaces();while(nets.hasMoreElements()){Enumeration<InetAddress> addresses=nets.nextElement().getInetAddresses();while(addresses.hasMoreElements()){InetAddress a=addresses.nextElement();if(a instanceof Inet4Address&&!a.isLoopbackAddress()&&a.isSiteLocalAddress())host=a.getHostAddress();}}
  if(host==null)throw new java.io.IOException();String url="http://"+host+":"+pairing.port()+"/"+pairing.token;
  BitMatrix matrix=new MultiFormatWriter().encode(url,BarcodeFormat.QR_CODE,320,320);Bitmap bitmap=Bitmap.createBitmap(320,320,Bitmap.Config.RGB_565);for(int y=0;y<320;y++)for(int x=0;x<320;x++)bitmap.setPixel(x,y,matrix.get(x,y)?0xff000000:0xffffffff);ImageView qr=new ImageView(this);qr.setImageBitmap(bitmap);page.addView(qr,new LinearLayout.LayoutParams(dp(230),dp(230)));page.addView(text(url,14));
 }catch(Throwable failure){closePairing();status.setText("请先连接局域网，或点击下方直接输入配置地址。");}
 button("直接输入配置地址",new Runnable(){public void run(){importUrlPage();}});
 button("返回片库",new Runnable(){public void run(){home();}});}
 void catalog(final String keyword,final int number){catalog(keyword,"",number);}
 void catalog(final String keyword,final String categoryId,final int number){
  base(selected.name);final int id=epoch;final LiteCatalog.Site site=selected;
  button("搜索此来源",new Runnable(){public void run(){search();}});
  if(!categories.isEmpty()&&keyword.length()==0){
   HorizontalScrollView catScroll=new HorizontalScrollView(this);
   LinearLayout catRow=new LinearLayout(this);catRow.setOrientation(0);
   catScroll.addView(catRow);page.addView(catScroll);
   addCategoryChip(catRow,"全部","",categoryId.length()==0);
   for(int i=0;i<Math.min(categories.size(),20);i++){
    final LiteCatalog.Category c=categories.get(i);
    addCategoryChip(catRow,c.name,c.id,c.id.equals(categoryId));
   }
  }
  work(new Job(){public void run()throws Exception{
   if(categories.isEmpty()&&keyword.length()==0&&categoryId.length()==0&&number==1){
    try{
     String homeRaw=LiteCatalog.fetch(LiteCatalog.query(site.api,"ac","list"),site.headers);
     List<LiteCatalog.Category> parsedCats=LiteCatalog.categories(homeRaw);
     if(!parsedCats.isEmpty())categories=parsedCats;
    }catch(Throwable ignored){}
   }
   String url=categoryId.length()>0
    ?LiteCatalog.query(site.api,"ac","detail","pg",""+number,"t",categoryId)
    :LiteCatalog.query(site.api,"ac","detail","pg",""+number,"wd",keyword);
   final JSONObject response=new JSONObject(LiteCatalog.fetch(url,site.headers));
   final JSONArray list=response.optJSONArray("list");if(list==null)throw new java.io.IOException();
   ui.post(new Runnable(){public void run(){
    getPreferences(0).edit().putString("loading_site","").commit();
    if(dead||id!=epoch)return;
    status.setText(list.length()==0?"没有结果":"第 "+number+" 页");
    for(int i=0;i<Math.min(list.length(),60);i++){final JSONObject video=list.optJSONObject(i);if(video==null)continue;button(video.optString("vod_name"),new Runnable(){public void run(){detail(video.optString("vod_id"));}});}
    if(number<response.optInt("pagecount",1))button("下一页",new Runnable(){public void run(){catalog(keyword,categoryId,number+1);}});
   }});
  }});
 }
 void addCategoryChip(LinearLayout row,String label,final String catId,boolean active){
  final TextView chip=text(label,15);
  chip.setFocusable(true);chip.setClickable(true);
  GradientDrawable bg=new GradientDrawable();
  bg.setColor(active?0xff0a84ff:0xff28282f);bg.setCornerRadius(dp(12));
  chip.setBackgroundDrawable(bg);
  LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(40));
  lp.setMargins(dp(4),dp(4),dp(4),dp(4));
  chip.setGravity(Gravity.CENTER);
  chip.setOnClickListener(new View.OnClickListener(){public void onClick(View v){catalog("",catId,1);}});
  row.addView(chip,lp);
 }
 void search(){base("搜索 · "+selected.name);final EditText input=new EditText(this);input.setSingleLine();input.setTextColor(0xffffffff);page.addView(input);button("搜索",new Runnable(){public void run(){catalog(input.getText().toString().trim(),1);}});}
 void detail(final String videoId){base("影片详情");final int id=epoch;final LiteCatalog.Site site=selected;work(new Job(){public void run()throws Exception{final JSONObject video=LiteCatalog.list(LiteCatalog.fetch(LiteCatalog.query(site.api,"ac","detail","ids",videoId),site.headers)).getJSONObject(0);ui.post(new Runnable(){public void run(){if(!dead&&epoch==id)showDetail(video);}});}});}
 void showDetail(final JSONObject video){base(video.optString("vod_name"));currentDetail=video;status.setText(android.text.Html.fromHtml(video.optString("vod_content")).toString());String[] lines=video.optString("vod_play_url").split("\\$\\$\\$");String[] names=video.optString("vod_play_from").split("\\$\\$\\$");for(int i=0;i<lines.length;i++){page.addView(text(i<names.length?names[i]:"线路",18));int count=0;for(String entry:lines[i].split("#")){if(++count>150){page.addView(text("每条线路暂展示前 150 集",16));break;}int sep=entry.indexOf('$');if(sep<0)continue;final String name=entry.substring(0,sep),url=entry.substring(sep+1);button(name,new Runnable(){public void run(){play(url,name);}});}}}
 void play(final String address,String name){
  base(name);inPlayback=true;final int id=epoch;
  playback=new FrameLayout(this);playback.setBackgroundColor(0xff000000);setContentView(playback);
  surface=new SurfaceView(this);playback.addView(surface,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));
  final LinearLayout topBar=new LinearLayout(this);topBar.setOrientation(0);topBar.setGravity(Gravity.CENTER_VERTICAL);topBar.setBackgroundColor(0x90101013);
  TextView backBtn=text("← 返回",16);backBtn.setClickable(true);backBtn.setOnClickListener(new View.OnClickListener(){public void onClick(View v){onBackPressed();}});
  topBar.addView(backBtn,new LinearLayout.LayoutParams(-2,-2));
  TextView heading=text(name,20);topBar.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
  playback.addView(topBar,new FrameLayout.LayoutParams(-1,dp(56),Gravity.TOP));
  final LinearLayout bottomPanel=new LinearLayout(this);bottomPanel.setOrientation(1);bottomPanel.setBackgroundColor(0xb0101013);bottomPanel.setPadding(dp(12),dp(6),dp(12),dp(8));
  LinearLayout progressRow=new LinearLayout(this);progressRow.setOrientation(0);progressRow.setGravity(Gravity.CENTER_VERTICAL);
  timeline=new SeekBar(this);
  clockView=text("00:00 / 00:00",13);
  progressRow.addView(timeline,new LinearLayout.LayoutParams(0,-2,1));
  progressRow.addView(clockView,new LinearLayout.LayoutParams(-2,-2));
  bottomPanel.addView(progressRow,new LinearLayout.LayoutParams(-1,-2));
  timeline.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
   public void onProgressChanged(SeekBar s,int progress,boolean fromUser){if(fromUser&&clockView!=null&&player!=null)clockView.setText(formatTime(progress)+" / "+formatTime(player.getDuration()));}
   public void onStartTrackingTouch(SeekBar s){sliderSeeking=true;}
   public void onStopTrackingTouch(SeekBar s){
    int target=s.getProgress();sliderSeeking=false;
    if(player!=null&&ready){lastUserSeekMs=target;lastUserSeekAt=SystemClock.uptimeMillis();try{player.seekTo(target);}catch(Exception ignored){}}
   }
  });
  status=text("正在准备播放…",15);
  bottomPanel.addView(status,new LinearLayout.LayoutParams(-1,-2));
  playback.addView(bottomPanel,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
  bindTouchSeekGesture(topBar,bottomPanel);
  surface.getHolder().addCallback(new SurfaceHolder.Callback(){
   public void surfaceCreated(SurfaceHolder holder){if(id!=epoch||dead)return;try{
    LiteCatalog.http(address);player=PlayerFactory.create(getPreferences(0).getBoolean("experimental_decoder",false));final LitePlayer expected=player;
    player.open(LiteActivity.this,address,selected==null?Collections.<String,String>emptyMap():selected.headers,holder,new LitePlayer.Listener(){
     public void prepared(){if(expected!=player)return;ready=true;expected.start();status.setText(expected.name()+" · 确认键暂停／播放 · 左右滑动或方向键快进快退 · 返回选集");ui.removeCallbacks(clockTick);ui.post(clockTick);}
     public void error(int what,int extra){if(expected!=player)return;ready=false;status.setText(expected.name()+"无法播放（"+what+"/"+extra+"），可返回选择其他资源。");}
     public void size(final int w,final int h){ui.post(new Runnable(){public void run(){if(expected!=player||w<=0||h<=0)return;int width=playback.getWidth(),height=playback.getHeight();if(width<=0||height<=0)return;double scale=Math.min((double)width/w,(double)height/h);surface.setLayoutParams(new FrameLayout.LayoutParams((int)(w*scale),(int)(h*scale),Gravity.CENTER));}});}
     public void completed(){if(expected==player)status.setText("播放结束 · 返回选集");}
    });
    ui.postDelayed(new Runnable(){public void run(){if(player==expected&&!ready)status.setText("播放准备较慢，可按返回选择其他资源。");}},15000);
   }catch(Exception failure){release();status.setText("无法打开播放地址，请返回选择其他资源。");}catch(LinkageError unavailable){release();status.setText("此设备无法加载实验解码器，请使用系统解码。");}}
   public void surfaceChanged(SurfaceHolder h,int f,int w,int height){}
   public void surfaceDestroyed(SurfaceHolder h){if(id==epoch)release();}
  });
 }
 void bindTouchSeekGesture(final View topBar,final View bottomPanel){
  final float[] downXY=new float[2];
  final int[] startPos=new int[1],targetPos=new int[1];
  final boolean[] dragging=new boolean[1];
  final int slop=ViewConfiguration.get(this).getScaledTouchSlop();
  playback.setOnTouchListener(new View.OnTouchListener(){
   public boolean onTouch(View v,MotionEvent ev){
    int act=ev.getActionMasked();
    if(act==MotionEvent.ACTION_DOWN){
     downXY[0]=ev.getRawX();downXY[1]=ev.getRawY();dragging[0]=false;
     startPos[0]=player!=null&&ready?Math.max(0,player.getCurrentPosition()):0;
     targetPos[0]=startPos[0];
     return true;
    }
    if(act==MotionEvent.ACTION_MOVE){
     float dx=ev.getRawX()-downXY[0],dy=ev.getRawY()-downXY[1];
     if(!dragging[0]&&Math.abs(dx)>slop&&Math.abs(dx)>Math.abs(dy)*1.2f&&player!=null&&ready&&player.getDuration()>0){
      dragging[0]=true;touchSeeking=true;
     }
     if(dragging[0]&&player!=null&&ready){
      int dur=player.getDuration();
      int width=Math.max(1,playback.getWidth());
      int delta=(int)((dx/width)*Math.min(dur,180000));
      targetPos[0]=Math.max(0,Math.min(dur,startPos[0]+delta));
      if(timeline!=null){timeline.setMax(dur);timeline.setProgress(targetPos[0]);}
      if(clockView!=null)clockView.setText(formatTime(targetPos[0])+" / "+formatTime(dur));
      status.setText((delta>=0?"快进至 ":"快退至 ")+formatTime(targetPos[0]));
     }
     return true;
    }
    if(act==MotionEvent.ACTION_UP||act==MotionEvent.ACTION_CANCEL){
     if(dragging[0]){
      dragging[0]=false;touchSeeking=false;
      if(player!=null&&ready&&player.getDuration()>0){
       lastUserSeekMs=targetPos[0];lastUserSeekAt=SystemClock.uptimeMillis();
       try{player.seekTo(targetPos[0]);status.setText("已跳转至 "+formatTime(targetPos[0]));}catch(Exception ignored){}
      }
     }else if(act==MotionEvent.ACTION_UP){
      int vis=topBar.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE;
      topBar.setVisibility(vis);bottomPanel.setVisibility(vis);
     }
     return true;
    }
    return false;
   }
  });
 }
 @Override public boolean dispatchKeyEvent(KeyEvent e){if(player!=null&&ready){int k=e.getKeyCode();if(k==KeyEvent.KEYCODE_DPAD_CENTER||k==KeyEvent.KEYCODE_ENTER||k==KeyEvent.KEYCODE_DPAD_LEFT||k==KeyEvent.KEYCODE_DPAD_RIGHT){if(e.getAction()==KeyEvent.ACTION_DOWN&&e.getRepeatCount()==0){try{if(k==KeyEvent.KEYCODE_DPAD_LEFT||k==KeyEvent.KEYCODE_DPAD_RIGHT){int target=Math.max(0,Math.min(player.getDuration(),player.getCurrentPosition()+(k==KeyEvent.KEYCODE_DPAD_LEFT?-15000:15000)));lastUserSeekMs=target;lastUserSeekAt=SystemClock.uptimeMillis();player.seekTo(target);}else if(player.isPlaying())player.pause();else player.start();}catch(IllegalStateException ignored){}}return true;}}return super.dispatchKeyEvent(e);}
 void release(){ready=false;touchSeeking=false;sliderSeeking=false;ui.removeCallbacks(clockTick);if(player!=null){player.release();player=null;}}
 void closePairing(){synchronized(importLock){if(pairing!=null){pairing.close();pairing=null;}}}
 @Override public void onBackPressed(){if(homeVisible){finish();return;}if(inPlayback&&currentDetail!=null){showDetail(currentDetail);return;}home();}
 @Override protected void onStart(){super.onStart();if(backgrounded){backgrounded=false;if(inPlayback&&currentDetail!=null)showDetail(currentDetail);else home();}}
 @Override protected void onStop(){super.onStop();backgrounded=true;epoch++;cancelWork();closePairing();release();}
 @Override protected void onDestroy(){dead=true;epoch++;cancelWork();closePairing();release();io.shutdownNow();super.onDestroy();}
}
