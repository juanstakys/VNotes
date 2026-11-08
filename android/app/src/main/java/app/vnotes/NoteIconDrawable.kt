package app.vnotes

import android.graphics.*
import android.graphics.drawable.Drawable

/** Same familiar symbols in both designs; the surrounding controls come from the selected theme. */
class NoteIconDrawable(var kind:String,private val color:Int):Drawable(){
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    override fun getIntrinsicWidth()=24
    override fun getIntrinsicHeight()=24
    override fun draw(c:Canvas){
        c.save();c.translate(bounds.exactCenterX(),bounds.exactCenterY());c.scale(bounds.width()/24f,bounds.height()/24f)
        paint.color=color;paint.style=Paint.Style.STROKE;paint.strokeWidth=1.7f;paint.strokeCap=Paint.Cap.ROUND
        when(kind){
            "plus"->{c.drawLine(-7f,0f,7f,0f,paint);c.drawLine(0f,-7f,0f,7f,paint)}
            "stop"->{paint.style=Paint.Style.FILL;c.drawRoundRect(-5f,-5f,5f,5f,1f,1f,paint)}
            "mic"->{c.drawRoundRect(-3f,-9f,3f,3f,3f,3f,paint);c.drawArc(-6f,-5f,6f,7f,0f,180f,false,paint);c.drawLine(0f,7f,0f,10f,paint)}
            "trash"->{c.drawLine(-7f,-6f,7f,-6f,paint);c.drawRect(-3f,-9f,3f,-6f,paint);c.drawRect(-5f,-6f,5f,9f,paint);c.drawLine(-2f,-2f,-2f,5f,paint);c.drawLine(2f,-2f,2f,5f,paint)}
            else->{c.drawCircle(0f,0f,6f,paint);c.drawCircle(0f,0f,2f,paint);repeat(8){i->val a=i*Math.PI/4;c.drawLine((kotlin.math.cos(a)*7).toFloat(),(kotlin.math.sin(a)*7).toFloat(),(kotlin.math.cos(a)*9).toFloat(),(kotlin.math.sin(a)*9).toFloat(),paint)}}
        };c.restore()
    }
    override fun setAlpha(alpha:Int){paint.alpha=alpha;invalidateSelf()}
    override fun setColorFilter(filter:ColorFilter?){paint.colorFilter=filter;invalidateSelf()}
    @Deprecated("Drawable opacity") override fun getOpacity()=PixelFormat.TRANSLUCENT
}
