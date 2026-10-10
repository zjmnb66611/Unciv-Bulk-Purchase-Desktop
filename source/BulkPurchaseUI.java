package unciv.civic;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.unciv.logic.city.City;
import com.unciv.ui.screens.basescreen.BaseScreen;
import com.unciv.ui.screens.cityscreen.CityScreen;
import com.unciv.ui.popups.Popup;
import com.unciv.ui.components.input.KeyboardBinding;
import com.unciv.models.stats.Stat;
import com.unciv.models.translations.TranslationsKt;
import kotlin.Unit;
import java.lang.reflect.Field;
import java.util.List;

/** 在原购买按钮旁附加入口；所有购买均需一次明确确认。 */
public final class BulkPurchaseUI {
    public static void attach(Object controller) {
        try {
            CityScreen screen=(CityScreen)field(controller,"cityScreen");
            if(!screen.getCanChangeState() || !screen.getCityView().isOwnedByViewer())return;
            Table table=(Table)field(controller,"buttonsTable");
            if(table.findActor("civic-bulk-purchase")!=null)return;
            TextButton button=new TextButton("购买队列",BaseScreen.skin);
            button.setName("civic-bulk-purchase");
            button.setDisabled(screen.getCityView().getConstructions().getConstructionQueue().isEmpty());
            button.addListener(new ClickListener(){@Override public void clicked(InputEvent event,float x,float y){
                if(!button.isDisabled())confirm(screen);
            }});
            table.add(button).width(150f).padLeft(8f);
        }catch(ReflectiveOperationException error){throw new IllegalStateException("批量购买界面挂接失败",error);}
    }
    static Object field(Object object,String name)throws ReflectiveOperationException{
        Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);
    }
    static void confirm(CityScreen screen){
        if(!screen.getCanChangeState() || !screen.getCityView().isOwnedByViewer())return;
        City city=screen.getCityView().getConstructions().getCityConstructions().getCity();
        List<String> queue=List.copyOf(city.getCityConstructions().getConstructionQueue());
        Popup popup=new Popup(screen,Popup.Scrollability.All,0.85f);
        popup.addGoodSizedLabel("购买当前城市队列",22,false,Color.WHITE).row();
        popup.addGoodSizedLabel("现有金币："+city.getStatReserve(Stat.Gold)+"\n队列项目："+queue.size()+
            "\n逐项按原版价格扣费；金币不足、奇观禁购或资源/位置不满足的项目会跳过。\n不会购买队列之外的项目，也不会扣除信仰。",18,false,Color.WHITE).row();
        popup.addCloseButton("取消",KeyboardBinding.Cancel,()->Unit.INSTANCE);
        popup.addOKButton("确认购买队列",KeyboardBinding.Confirm,BaseScreen.skin.get("positive",TextButton.TextButtonStyle.class),()->{
            if(!screen.getCanChangeState() || !screen.getCityView().isOwnedByViewer())return Unit.INSTANCE;
            var result=BulkPurchase.execute(city,queue);
            screen.setSelectedQueueEntry(-1);screen.clearSelection();screen.updateAsync$Unciv_core();
            Popup report=new Popup(screen,Popup.Scrollability.All,0.85f);
            StringBuilder text=new StringBuilder(result.message()).append("\n已购买 ").append(result.bought()).append(" 项，实际扣除 ").append(result.spent()).append(" 金币。");
            for(var entry:result.entries())text.append("\n").append(TranslationsKt.tr(entry.name(),true,false)).append("：").append(entry.purchased()?"已购（"+entry.cost()+"金币）":entry.reason());
            report.addGoodSizedLabel(text.toString(),18,false,Color.WHITE).row();
            report.addCloseButton("关闭",KeyboardBinding.Cancel,()->Unit.INSTANCE);report.open(true);
            return Unit.INSTANCE;
        });
        popup.open(true);
    }
}
