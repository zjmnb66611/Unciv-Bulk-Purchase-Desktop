package unciv.civic;

import com.unciv.logic.city.City;
import com.unciv.models.ruleset.INonPerpetualConstruction;
import com.unciv.models.stats.Stat;
import java.util.*;

/** 原生购买逐项执行；不绕过科技、资源、价格、放置空间和城市状态。 */
public final class BulkPurchase {
    public record Entry(String name, boolean purchased, int cost, String reason) {}
    public record Result(int spent, List<Entry> entries, String message) {
        public long bought() { return entries.stream().filter(Entry::purchased).count(); }
    }
    public static Result execute(City city, List<String> expectedQueue) {
        var constructions=city.getCityConstructions();
        var snapshot=List.copyOf(constructions.getConstructionQueue());
        if(!snapshot.equals(expectedQueue)) return new Result(0,List.of(),"队列已变化，请重新打开购买窗口。");
        if(city.getCiv().getGameInfo().getGameParameters().getGodMode())
            return new Result(0,List.of(),"请先关闭神模式，批量购买要求正常扣除金币。");
        var results=new ArrayList<Entry>(); int initial=city.getStatReserve(Stat.Gold);
        for(String name:snapshot) {
            int index=constructions.getConstructionQueue().indexOf(name);
            if(index<0) { results.add(new Entry(name,false,0,"已被原版队列校验移除")); continue; }
            var construction=constructions.getConstruction$Unciv_core(name);
            if(!(construction instanceof INonPerpetualConstruction item)) {
                results.add(new Entry(name,false,0,"持续项目不能购买"));continue;
            }
            Integer cost=item.getStatBuyCost(city,Stat.Gold);
            if(cost==null || !item.canBePurchasedWithStat(city,Stat.Gold)) {
                results.add(new Entry(name,false,0,"不支持金币购买"));continue;
            }
            if(!constructions.isConstructionPurchaseAllowed(item,Stat.Gold,cost)) {
                String reason=city.getStatReserve(Stat.Gold)<cost?"金币不足":
                    constructions.isConstructionPurchaseBlockedByUnit(item)?"单位没有可放置的位置":"城市状态、资源或前置条件不满足";
                results.add(new Entry(name,false,0,reason));continue;
            }
            int before=city.getStatReserve(Stat.Gold);
            boolean success=constructions.purchaseConstruction(item,index,false,Stat.Gold,null);
            int paid=before-city.getStatReserve(Stat.Gold);
            results.add(new Entry(name,success,paid,success?"已购买":"原版购买未完成"));
        }
        return new Result(initial-city.getStatReserve(Stat.Gold),List.copyOf(results),"批量购买完成");
    }
}
