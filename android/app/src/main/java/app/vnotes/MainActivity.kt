package app.vnotes

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.Locale
import kotlin.math.*

class MainActivity : Activity() {
    private val ink=Color.rgb(37,49,40); private val mint=Color.rgb(185,239,193); private val border=Color.rgb(206,215,207)
    private val ui=Handler(Looper.getMainLooper());private val executor=Executors.newSingleThreadExecutor()
    private lateinit var store:NoteStore;private lateinit var retry:RetryStore;private lateinit var speech:SpeechEngine
    private val prefs by lazy{getSharedPreferences("settings",MODE_PRIVATE)}
    private var threshold=.01;private var silence=2.0;private var done=false;private var ready=false;private var busy=false;private var capturing=false;private var finishing=false
    private var transcribing=false;private var cancelTranscription=false
    private var liveSession:LiveSpeechSession?=null;private var liveGeneration=0
    @Volatile private var cancelTake=false
    private lateinit var liveText:TextView
    private var pending:PendingAudio?=null;private var target:String?=null;private var capture:AudioCapture?=null
    private var settingsDialog:AlertDialog?=null;private var testCapture:AudioCapture?=null;private var permissionAction:(()->Unit)?=null
    private lateinit var todoTab:Button;private lateinit var doneTab:Button;private lateinit var rows:LinearLayout;private lateinit var status:TextView
    private lateinit var setup:Button;private lateinit var draft:EditText;private lateinit var slot:FrameLayout;private lateinit var feedback:LinearLayout;private lateinit var level:LevelView;private lateinit var elapsed:TextView
    private lateinit var snackbar:UndoSnackbar
    private var swipeX=0f;private var swipeY=0f;private var swipeInDone=false
    private var navigatingSwipe=false;private var verticalSwipe=false;private var multiTouchSwipe=false
    private lateinit var action:IconButton;private lateinit var cancel:Button;private lateinit var retryRow:LinearLayout
    private fun dp(n:Int)=(n*resources.displayMetrics.density).roundToInt()
    private fun bg(color:Int=Color.WHITE,radius:Int=12,stroke:Boolean=true)=GradientDrawable().apply{setColor(color);cornerRadius=dp(radius).toFloat();if(stroke)setStroke(dp(1),border)}
    private fun label(text:String,size:Float=14f)=TextView(this).apply{this.text=text;textSize=size;setTextColor(ink);setPadding(dp(4),dp(8),dp(4),dp(8))}
    private fun button(text:String, click:()->Unit)=Button(this).apply{this.text=text;isAllCaps=false;setTextColor(ink);textSize=14f;setOnClickListener{click()}}
    private fun post(task:()->Unit){ui.post{if(!isDestroyed)task()}}
    private fun error(e:Throwable){post{status.text=e.message?:e.javaClass.simpleName;status.visibility=View.VISIBLE;updateControls()}}
    override fun onCreate(state:Bundle?){
        super.onCreate(state)
        store=NoteStore(this);retry=RetryStore(this);speech=SpeechEngine(applicationContext)
        threshold=prefs.getFloat("threshold",.01f).toDouble().coerceIn(.00001,.1);silence=prefs.getFloat("silence",2f).toDouble()
        buildUi();try{pending=retry.load()}catch(e:Throwable){error(e)};render()
        executor.execute{try{val present=speech.present();post{ready=present;render()}}catch(e:Throwable){error(e)}}
    }
    private fun buildUi(){
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(Color.WHITE);isClickable=true;importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO}
        val tabs=LinearLayout(this).apply{setPadding(dp(12),dp(8),dp(12),dp(8))}
        todoTab=button("TODO"){switchScreen(false)};doneTab=button("DONE"){switchScreen(true)}
        tabs.addView(todoTab,LinearLayout.LayoutParams(0,dp(46),1f));tabs.addView(doneTab,LinearLayout.LayoutParams(0,dp(46),1f).apply{leftMargin=dp(10)})
        root.addView(tabs);root.addView(View(this).apply{setBackgroundColor(border)},LinearLayout.LayoutParams(-1,dp(1)))
        val scroll=ScrollView(this);val content=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(24),0,dp(24),dp(12))}
        rows=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};content.addView(rows)
        setup=button("Download English voice model"){setupModel()};content.addView(setup);scroll.addView(content);root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        val bottom=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(18),dp(8),dp(18),dp(18))}
        liveText=label("",16f).apply{
            maxLines=5;visibility=View.GONE;isVerticalScrollBarEnabled=true
            movementMethod=android.text.method.ScrollingMovementMethod.getInstance()
        };bottom.addView(liveText)
        status=label("");status.visibility=View.GONE;bottom.addView(status)
        cancel=button("Cancel"){if(capturing){liveText.text="";status.visibility=View.GONE;cancelTake=true;liveGeneration++;liveSession?.cancel();liveSession=null;finishing=true;capture?.stop(true);updateControls()}else if(transcribing){cancelTranscription=true;liveSession?.cancel();status.text="Cancelling… Recording kept for retry.";updateControls()}};cancel.visibility=View.GONE;bottom.addView(cancel,LinearLayout.LayoutParams(-2,dp(40)).apply{gravity=Gravity.END})
        retryRow=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
        retryRow.addView(label("Saved recording"),LinearLayout.LayoutParams(0,-2,1f));retryRow.addView(button("Retry"){transcribe()});retryRow.addView(button("Discard"){pending=null;retry.clear();updateControls()});bottom.addView(retryRow)
        val controls=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
        controls.addView(IconButton("settings","Settings"){openSettings()},LinearLayout.LayoutParams(dp(44),dp(44)))
        slot=FrameLayout(this).apply{background=bg()}
        draft=EditText(this).apply{hint="Your task here…";textSize=16f;setTextColor(ink);setHintTextColor(Color.GRAY);background=null;setPadding(dp(12),dp(8),dp(12),dp(8));maxLines=5;filters=arrayOf(InputFilter.LengthFilter(20000))}
        slot.addView(draft,FrameLayout.LayoutParams(-1,-2,Gravity.CENTER_VERTICAL))
        feedback=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL;setPadding(dp(12),0,dp(12),0)}
        level=LevelView();feedback.addView(level,LinearLayout.LayoutParams(0,dp(20),1f));elapsed=label("0:00",11f);feedback.addView(elapsed);slot.addView(feedback,FrameLayout.LayoutParams(-1,dp(44)))
        controls.addView(slot,LinearLayout.LayoutParams(0,-2,1f).apply{leftMargin=dp(12);rightMargin=dp(12)})
        action=IconButton("mic","Record a note"){primaryAction()};controls.addView(action,LinearLayout.LayoutParams(dp(44),dp(44)))
        snackbar=UndoSnackbar(this);bottom.addView(snackbar,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(8)})
        bottom.addView(controls);root.addView(bottom)
        draft.addTextChangedListener(object:TextWatcher{override fun beforeTextChanged(s:CharSequence?,a:Int,c:Int,d:Int){};override fun onTextChanged(s:CharSequence?,a:Int,b:Int,c:Int){updateControls()};override fun afterTextChanged(s:Editable?){} })
        setContentView(root)
    }
    private fun render(){
        todoTab.background=bg(if(!done)mint else Color.WHITE);doneTab.background=bg(if(done)mint else Color.WHITE)
        todoTab.isSelected=!done;doneTab.isSelected=done
        rows.removeAllViews();val notes=store.all().filter{(it.deleted!=null)==done}.sortedByDescending{if(done)it.deleted!! else it.created}
        notes.forEach{n ->
            val row=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL;minimumHeight=dp(66)}
            val text=label(n.text,17f);row.addView(text,LinearLayout.LayoutParams(0,-2,1f))
            val menu=button("···"){noteMenu(n)};menu.contentDescription="Actions for ${n.text.take(50)}";row.addView(menu,LinearLayout.LayoutParams(dp(38),dp(44)))
            // Only TODO's right swipe completes an item. Left swipes and all DONE swipes
            // are handled by Activity.dispatchTouchEvent, including gestures on empty space.
            if(!done){
                var x=0f;var y=0f;var horizontal=false;var blocked=false
                row.setOnTouchListener{v,event -> when(event.actionMasked){
                    MotionEvent.ACTION_DOWN->{x=event.rawX;y=event.rawY;horizontal=false;blocked=false;true}
                    MotionEvent.ACTION_MOVE->{val dx=event.rawX-x;val dy=event.rawY-y
                        if(!horizontal&&abs(dy)>dp(12)&&abs(dy)*1.3f>=abs(dx))blocked=true
                        if(!blocked&&dx>dp(12)&&dx>abs(dy)*1.3f)horizontal=true
                        if(horizontal){v.parent.requestDisallowInterceptTouchEvent(true);v.translationX=dx.coerceIn(0f,dp(120).toFloat())};true}
                    MotionEvent.ACTION_UP->{val complete=!blocked&&completesNote(done,event.rawX-x,event.rawY-y,dp(80).toFloat());v.translationX=0f;if(complete)move(n,true);true}
                    MotionEvent.ACTION_POINTER_DOWN->{blocked=true;horizontal=false;v.translationX=0f;true}
                    MotionEvent.ACTION_CANCEL->{v.translationX=0f;horizontal=false;blocked=true;true};else->false}
                }
            }
            rows.addView(row);rows.addView(View(this).apply{setBackgroundColor(border)},LinearLayout.LayoutParams(-1,dp(1)))
        };setup.visibility=if(!ready&&!done)View.VISIBLE else View.GONE;updateControls()
    }
    private fun switchScreen(showDone:Boolean){
        if(capturing||finishing||done==showDone)return
        done=showDone
        if(done){(getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(draft.windowToken,0);draft.clearFocus()}
        render()
    }
    private fun cancelChildTouch(event:MotionEvent){
        val cancelEvent=MotionEvent.obtain(event);cancelEvent.action=MotionEvent.ACTION_CANCEL
        try{super.dispatchTouchEvent(cancelEvent)}finally{cancelEvent.recycle()}
    }
    override fun dispatchTouchEvent(event:MotionEvent):Boolean{
        // Dialogs receive their own Window events, so settings sliders and edit fields retain
        // their normal gestures. This observer covers every part of the main screen.
        when(event.actionMasked){
            MotionEvent.ACTION_DOWN->{swipeX=event.rawX;swipeY=event.rawY;swipeInDone=done;navigatingSwipe=false;verticalSwipe=false;multiTouchSwipe=false}
            MotionEvent.ACTION_POINTER_DOWN->{multiTouchSwipe=true;if(navigatingSwipe)cancelChildTouch(event)}
            MotionEvent.ACTION_MOVE->{
                if(!multiTouchSwipe&&!verticalSwipe&&!capturing&&!finishing){
                    val dx=event.rawX-swipeX;val dy=event.rawY-swipeY
                    if(!navigatingSwipe&&abs(dy)>dp(12)&&abs(dy)*1.3f>=abs(dx))verticalSwipe=true
                    else if(!navigatingSwipe&&screenSwipe(swipeInDone,dx,dy,dp(12).toFloat())!=ScreenSwipe.NONE){navigatingSwipe=true;cancelChildTouch(event)}
                }
                if(navigatingSwipe)return true
            }
            MotionEvent.ACTION_UP->{
                val destination=if(!multiTouchSwipe&&!verticalSwipe&&!capturing&&!finishing)screenSwipe(swipeInDone,event.rawX-swipeX,event.rawY-swipeY,dp(80).toFloat())else ScreenSwipe.NONE
                if(navigatingSwipe||destination!=ScreenSwipe.NONE){
                    if(!navigatingSwipe)cancelChildTouch(event)
                    navigatingSwipe=false
                    if(destination!=ScreenSwipe.NONE)switchScreen(destination==ScreenSwipe.DONE)
                    return true
                }
            }
            MotionEvent.ACTION_CANCEL->{val consumed=navigatingSwipe;navigatingSwipe=false;multiTouchSwipe=false;if(consumed)return true}
        }
        return super.dispatchTouchEvent(event)
    }
    private fun updateControls(){
        if(!::draft.isInitialized||!::action.isInitialized)return
        liveText.visibility=if((capturing||finishing||transcribing)&&liveText.text.isNotBlank())View.VISIBLE else View.GONE
        draft.visibility=if(capturing||done)View.GONE else View.VISIBLE;draft.isEnabled=!finishing
        feedback.visibility=if(capturing)View.VISIBLE else View.GONE;slot.visibility=if(done)View.INVISIBLE else View.VISIBLE
        cancel.visibility=if(capturing||transcribing)View.VISIBLE else View.GONE;cancel.isEnabled=!finishing&&!cancelTranscription
        action.kind=when{done->"trash";capturing->"stop";draft.text.toString().isNotBlank()->"plus";else->"mic"}
        action.contentDescription=when(action.kind){"trash"->"Erase all done notes";"stop"->"Stop recording";"plus"->"Add note";else->"Record a note"}
        action.isEnabled=!finishing && if(done)store.all().any{it.deleted!=null} else capturing || (!busy || draft.text.toString().isNotBlank())
        action.invalidate();retryRow.visibility=if(pending!=null&&!busy&&!capturing)View.VISIBLE else View.GONE
        setup.isEnabled=!busy
    }
    private fun primaryAction(){
        try{
            if(done){AlertDialog.Builder(this).setTitle("Empty DONE?").setMessage("All done notes will be erased permanently.").setNegativeButton("Cancel",null).setPositiveButton("Erase all"){_,_->snackbar.dismiss();store.emptyDone();render()}.show();return}
            if(capturing){finishing=true;capture?.stop();updateControls();return}
            val text=draft.text.toString().trim();if(text.isNotBlank()){store.add(text);draft.setText("");render();return}
            startRecording(null)
        }catch(e:Throwable){error(e)}
    }
    private fun noteMenu(n:Note){AlertDialog.Builder(this).setItems(if(done)arrayOf("Restore","Erase forever")else arrayOf("Edit","Done")){_,i->if(done){if(i==0)move(n,false)else erase(n)}else{if(i==0)chooseEdit(n)else move(n,true)}}.show()}
    private fun move(n:Note,deleted:Boolean){try{store.save(n.copy(created=if(deleted)n.created else System.currentTimeMillis(),deleted=if(deleted)System.currentTimeMillis()else null));render()
        snackbar.show(if(deleted)"Moved to DONE" else "Restored to TODO"){
            try{
                // Preserve any subsequent text edit and never resurrect a permanently erased item.
                store.all().find{it.id==n.id}?.let{current->store.save(current.copy(created=n.created,deleted=n.deleted))}
                render()
            }catch(e:Throwable){error(e)}
        }
    }catch(e:Throwable){error(e)}}
    private fun erase(n:Note){AlertDialog.Builder(this).setTitle("Erase this note forever?").setMessage("This cannot be undone.").setNegativeButton("Cancel",null).setPositiveButton("Erase"){_,_->snackbar.dismiss();store.erase(n.id);render()}.show()}
    private fun chooseEdit(n:Note){if(capturing||busy||finishing)return;AlertDialog.Builder(this).setTitle("Edit note").setItems(arrayOf("Type","Record")){_,i->if(i==0)edit(n,n.text,false)else startRecording(n.id)}.setNegativeButton("Cancel",null).show()}
    private fun edit(n:Note,text:String,recorded:Boolean){
        val input=EditText(this).apply{setText(text);setSelection(text.length);minLines=3;filters=arrayOf(InputFilter.LengthFilter(20000));setPadding(dp(20),dp(10),dp(20),dp(10))}
        val builder=AlertDialog.Builder(this).setTitle(if(recorded)"Review recording" else "Edit note").setView(input).setNegativeButton("Cancel",null).setPositiveButton("Save",null)
        if(recorded)builder.setNeutralButton("Record again"){_,_->startRecording(n.id)}
        val dialog=builder.create();dialog.setOnShowListener{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{
            val value=input.text.toString().trim();if(value.isEmpty())input.error="Enter a note"else{try{store.save(n.copy(text=value,updated=System.currentTimeMillis()));render();dialog.dismiss()}catch(e:Throwable){error(e)}}}}
        dialog.show()
    }
    private fun permission(action:()->Unit){if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)action()else{permissionAction=action;requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),41)}}
    override fun onRequestPermissionsResult(request:Int,permissions:Array<out String>,results:IntArray){super.onRequestPermissionsResult(request,permissions,results);if(request==41){val next=permissionAction;permissionAction=null;if(results.firstOrNull()==PackageManager.PERMISSION_GRANTED)next?.invoke()else error(IllegalStateException("Microphone permission denied. Enable it in Android app settings."))}}
    private fun startRecording(id:String?){
        if(busy||capturing||finishing)return
        if(pending!=null){status.text="Retry or discard the saved recording first.";status.visibility=View.VISIBLE;return}
        if(!ready){status.text="Download the English model first.";status.visibility=View.VISIBLE;return}
        permission{beginCapture(id)}
    }
    private fun beginCapture(id:String?){
        if(busy||capturing)return
        target=id;cancelTake=false;cancelTranscription=false
        val generation=++liveGeneration
        liveText.text="";liveText.scrollTo(0,0)
        val session=LiveSpeechSession(executor,object:LiveSpeech{
            override fun start(onText:(String)->Unit)=speech.startLive(onText)
            override fun feed(audio:FloatArray)=speech.feedLive(audio)
            override fun finish()=speech.finishLive()
            override fun abort()=speech.abortLive()
        },onText={text->post{
            if(generation==liveGeneration&&!cancelTranscription&&(capturing||finishing||transcribing)){
                liveText.text=text;updateControls()
                liveText.post{liveText.layout?.let{layout->liveText.scrollTo(0,maxOf(0,layout.height-liveText.height+liveText.totalPaddingTop+liveText.totalPaddingBottom))}}
            }
        }},onError={e->post{
            if(generation==liveGeneration&&capturing){status.text="Live text paused: ${e.message}. Audio is still being recorded.";status.visibility=View.VISIBLE}
        }})
        liveSession=session
        val current=AudioCapture();capture=current
        try{
            current.start(false,silence,threshold,
                onLevel={rms,seconds,_->post{level.value=EnergyVad.level(rms);elapsed.text=String.format(Locale.US,"%d:%02d",seconds.toInt()/60,seconds.toInt()%60)}},
                onAudio={audio->session.offer(audio)},
                onDone={audio,usable,_->
                    var saved:PendingAudio?=null;var saveError:Throwable?=null
                    if(audio!=null&&usable&&!cancelTake)try{saved=PendingAudio(audio,id);retry.save(saved)}catch(e:Throwable){saveError=e}
                    if(audio==null||!usable||saveError!=null||cancelTake)session.cancel()
                    post{
                        capturing=false;finishing=false;capture=null
                        when {
                            cancelTake->{if(saved!=null)retry.clear();liveSession=null;liveText.text="";updateControls()}
                            saveError!=null->{liveSession=null;liveText.text="";error(saveError!!)}
                            saved!=null->{pending=saved;finishLiveTake(session,generation)}
                            else->{liveSession=null;liveText.text="";updateControls();if(audio!=null){status.text="No speech detected. Adjust sensitivity in Settings.";status.visibility=View.VISIBLE}}
                        }
                    }
                },
                onError={e->session.cancel();post{liveGeneration++;liveSession=null;capturing=false;finishing=false;capture=null;liveText.text="";error(e)}}
            )
            capturing=true;elapsed.text="0:00";level.value=0f;status.visibility=View.GONE
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(draft.windowToken,0);draft.clearFocus();updateControls()
        }catch(e:Throwable){session.cancel();liveGeneration++;liveSession=null;capture=null;error(e)}
    }
    private fun finishLiveTake(session:LiveSpeechSession,generation:Int){
        val p=pending?:return
        busy=true;transcribing=true;status.text="Finishing transcription…";status.visibility=View.VISIBLE;updateControls()
        session.finish{result->post{
            if(generation!=liveGeneration)return@post
            liveSession=null
            when {
                cancelTranscription->completeTranscript(p,"")
                result.isSuccess->completeTranscript(p,result.getOrThrow())
                else->{busy=false;transcribing=false;transcribe()} // Durable PCM remains available if the stream failed.
            }
        }}
    }
    private fun completeTranscript(p:PendingAudio,text:String){
        try{
            if(cancelTranscription){status.text="Transcription cancelled. Recording kept for retry.";return}
            if(p.target==null){val now=System.currentTimeMillis();store.save(Note(p.id,text,now,now,null))}
            else{val n=store.all().find{it.id==p.target}?:error("The note no longer exists");edit(n,text,true)}
            retry.clear();pending=null;status.visibility=View.GONE
        }catch(e:Throwable){error(e)}finally{busy=false;transcribing=false;finishing=false;liveText.text="";render()}
    }
    private fun setupModel(){if(busy)return;busy=true;status.text="Downloading English model…";status.visibility=View.VISIBLE;updateControls()
        executor.execute{try{speech.setup{message->post{status.text=message}};post{ready=true;busy=false;status.visibility=View.GONE;render()}}catch(e:Throwable){post{busy=false;error(e);render()}}}}
    private fun transcribe(){
        val p=pending?:return;if(busy)return;busy=true;transcribing=true;cancelTranscription=false;status.text="Transcribing…";status.visibility=View.VISIBLE;updateControls()
        executor.execute{try{val text=speech.transcribe(p.audio){message->post{if(!cancelTranscription)status.text=message}};post{completeTranscript(p,text)}}
            catch(e:Throwable){post{busy=false;transcribing=false;liveText.text="";error(e);updateControls()}}}
    }
    private fun stopTest(){testCapture?.stop(true);testCapture=null}
    private fun openSettings(){
        if(capturing||finishing)return
        val panel=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(24),dp(8),dp(24),dp(8))}
        panel.addView(label("Stop after silence"));val spinner=Spinner(this);val choices=arrayOf("0.7 seconds","1 second","2 seconds","3 seconds","5 seconds");val times=doubleArrayOf(.7,1.0,2.0,3.0,5.0)
        spinner.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,choices);spinner.setSelection(times.indices.minByOrNull{abs(times[it]-silence)}?:2);panel.addView(spinner)
        spinner.onItemSelectedListener=object:AdapterView.OnItemSelectedListener{override fun onNothingSelected(p:AdapterView<*>?){};override fun onItemSelected(p:AdapterView<*>?,v:View?,i:Int,id:Long){silence=times[i];prefs.edit().putFloat("silence",silence.toFloat()).apply()}}
        panel.addView(label("Microphone sensitivity"));val amount=label("");val slider=SeekBar(this).apply{max=1000;progress=EnergyVad.position(threshold)};panel.addView(slider);panel.addView(amount)
        val meter=LevelView().apply{marker=EnergyVad.level(threshold)};panel.addView(meter,LinearLayout.LayoutParams(-1,dp(24)))
        val testStatus=label("Slide right for quieter sounds. Test without saving audio.",12f);panel.addView(testStatus);var rms=0.0
        fun feedback(){amount.text=String.format(Locale.US,"%.1f× normal · %.5f RMS",.01/threshold,threshold);meter.marker=EnergyVad.level(threshold);if(testCapture!=null)testStatus.text=String.format(Locale.US,"%s · %.5f RMS",if(rms>=threshold)"Sound detected" else "Quiet",rms)}
        slider.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{override fun onProgressChanged(s:SeekBar?,value:Int,user:Boolean){if(user){threshold=EnergyVad.threshold(value);prefs.edit().putFloat("threshold",threshold.toFloat()).apply();feedback()}};override fun onStartTrackingTouch(s:SeekBar?){};override fun onStopTrackingTouch(s:SeekBar?){}});feedback()
        val test=button("Test microphone"){};test.setOnClickListener{
            if(testCapture!=null){stopTest();test.text="Test microphone";testStatus.text="Test stopped";meter.value=0f}else permission{
                if(settingsDialog?.isShowing!=true)return@permission
                val c=AudioCapture();try{c.start(true,silence,threshold,onLevel={value,_,_->post{rms=value;meter.value=EnergyVad.level(value);feedback()}},onDone={_,_,_->},onError={e->post{stopTest();test.text="Test microphone";testStatus.text=e.message}});testCapture=c;test.text="Stop test"}catch(e:Throwable){testStatus.text=e.message}
            }
        };panel.addView(test)
        panel.addView(button(if(ready)"Check voice model" else "Download voice model"){settingsDialog?.dismiss();setupModel()})
        panel.addView(button("Export backup"){exportBackup()});panel.addView(button("Import backup"){importBackup()})
        panel.addView(label("VNotes 3.2 · native Kotlin\nEnglish Moonshine Tiny · local processing",12f))
        val scroll=ScrollView(this).apply{addView(panel)}
        settingsDialog=AlertDialog.Builder(this).setTitle("Settings").setView(scroll).setPositiveButton("Done",null).create().also{d->d.setOnDismissListener{stopTest();permissionAction=null;settingsDialog=null};d.show()}
    }
    private fun exportBackup(){startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="application/json";putExtra(Intent.EXTRA_TITLE,"vnotes-backup.json")},51)}
    private fun importBackup(){startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="application/json"},52)}
    @Deprecated("Platform document picker callback")
    override fun onActivityResult(request:Int,result:Int,data:Intent?){super.onActivityResult(request,result,data);if(result!=RESULT_OK)return;val uri=data?.data?:return
        try{if(request==51){val json=JSONObject().put("format","vnotes").put("version",1).put("notes",JSONArray().apply{store.all().forEach{put(it.json())}}).put("settings",JSONObject().put("silence",silence).put("threshold",threshold))
            contentResolver.openOutputStream(uri,"wt")!!.bufferedWriter().use{it.write(json.toString(2))}
        }else if(request==52){val bytes=contentResolver.openInputStream(uri)!!.use{stream -> val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192);while(out.size()<=10_000_000){val n=stream.read(buffer);if(n<0)break;out.write(buffer,0,n)};out.toByteArray()};require(bytes.size<=10_000_000){"Backup exceeds 10 MB"};val notes=NoteStore.parseBackup(bytes.toString(Charsets.UTF_8));val existing=store.all().map{it.id}.toSet();val count=notes.count{it.id !in existing}
            AlertDialog.Builder(this).setTitle("Import $count notes?").setMessage("Existing notes and settings will be preserved.").setNegativeButton("Cancel",null).setPositiveButton("Import"){_,_->try{store.merge(notes);render()}catch(e:Throwable){error(e)}}.show()
        }}catch(e:Throwable){error(e)}
    }
    override fun onStop(){super.onStop();snackbar.dismiss();stopTest();settingsDialog?.dismiss();permissionAction=null;if(capturing){finishing=true;capture?.stop(false,"background");updateControls()}}
    override fun onDestroy(){capture?.stop(true);liveGeneration++;liveSession?.cancel();stopTest();executor.execute{speech.close()};executor.shutdown();super.onDestroy()}

    private inner class LevelView:View(this@MainActivity){
        var value=0f;set(v){field=v;invalidate();contentDescription="Microphone level ${(v*100).toInt()} percent"}
        var marker:Float?=null;set(v){field=v;invalidate()}
        private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(c:Canvas){super.onDraw(c);val top=height*.22f;val bottom=height*.78f;paint.color=Color.rgb(233,240,233);c.drawRoundRect(0f,top,width.toFloat(),bottom,dp(4).toFloat(),dp(4).toFloat(),paint);paint.color=Color.rgb(65,117,76);c.drawRoundRect(0f,top,width*value,bottom,dp(4).toFloat(),dp(4).toFloat(),paint);marker?.let{paint.color=Color.rgb(165,76,63);c.drawRect(width*it-dp(1),0f,width*it+dp(1),height.toFloat(),paint)}}
    }
    private inner class IconButton(var kind:String,description:String,click:()->Unit):View(this@MainActivity){
        private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        init{contentDescription=description;isClickable=true;isFocusable=true;background=bg(mint,50);setOnClickListener{click()}}
        override fun getAccessibilityClassName():CharSequence="android.widget.Button"
        override fun onDraw(canvas:Canvas){super.onDraw(canvas);canvas.save();canvas.translate(width/2f,height/2f);canvas.scale(dp(1).toFloat(),dp(1).toFloat());paint.color=ink;paint.style=Paint.Style.STROKE;paint.strokeWidth=1.7f;paint.strokeCap=Paint.Cap.ROUND
            when(kind){
                "plus"->{canvas.drawLine(-7f,0f,7f,0f,paint);canvas.drawLine(0f,-7f,0f,7f,paint)}
                "stop"->{paint.style=Paint.Style.FILL;canvas.drawRoundRect(-5f,-5f,5f,5f,1f,1f,paint)}
                "mic"->{canvas.drawRoundRect(-3f,-9f,3f,3f,3f,3f,paint);canvas.drawArc(RectF(-6f,-5f,6f,7f),0f,180f,false,paint);canvas.drawLine(0f,7f,0f,10f,paint)}
                "trash"->{canvas.drawLine(-7f,-6f,7f,-6f,paint);canvas.drawRect(-3f,-9f,3f,-6f,paint);canvas.drawRect(-5f,-6f,5f,9f,paint);canvas.drawLine(-2f,-2f,-2f,5f,paint);canvas.drawLine(2f,-2f,2f,5f,paint)}
                else->{canvas.drawCircle(0f,0f,6f,paint);canvas.drawCircle(0f,0f,2f,paint);for(i in 0..7){val a=i*Math.PI/4;canvas.drawLine((cos(a)*7).toFloat(),(sin(a)*7).toFloat(),(cos(a)*9).toFloat(),(sin(a)*9).toFloat(),paint)}}
            };canvas.restore()
        }
    }
}
