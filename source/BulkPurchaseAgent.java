package unciv.civic;
import java.lang.instrument.*;
import java.security.*;
import java.util.HexFormat;
import org.objectweb.asm.*;

/** 只在内存中挂接已验证版本的城市按钮更新函数。 */
public final class BulkPurchaseAgent {
    public static volatile boolean active;
    static final String TARGET="com/unciv/ui/screens/cityscreen/CityConstructionsTable";
    public static void premain(String args,Instrumentation instrumentation)throws Exception{
        instrumentation.addTransformer(new ClassFileTransformer(){
            @Override public byte[] transform(ClassLoader loader,String name,Class<?> type,ProtectionDomain domain,byte[] bytes){
                if(!TARGET.equals(name))return null;
                try{return patch(bytes);}catch(Throwable error){
                    System.err.println("[批量购买] 版本校验失败，拒绝加载："+error);Runtime.getRuntime().halt(43);return null;
                }
            }
        });
        Class.forName(TARGET.replace('/','.'),false,ClassLoader.getSystemClassLoader());
        if(!active)throw new IllegalStateException("批量购买按钮未挂接");
        System.out.println("[批量购买] 已挂接城市购买队列按钮；等待用户操作。");
    }
    static byte[] patch(byte[] bytes)throws Exception{
        String expected;
        try(var in=BulkPurchaseAgent.class.getResourceAsStream("/target-class.sha256")){
            if(in==null)throw new IllegalStateException("缺少版本校验数据");expected=new String(in.readAllBytes()).trim();
        }
        if(!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).equals(expected))throw new IllegalStateException("CityConstructionsTable不匹配");
        ClassReader reader=new ClassReader(bytes);ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);int[] found={0};
        reader.accept(new ClassVisitor(Opcodes.ASM9,writer){
            @Override public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions){
                MethodVisitor original=super.visitMethod(access,name,descriptor,signature,exceptions);
                if(!name.equals("updateButtons") || !descriptor.equals("(Lcom/unciv/models/ruleset/IConstruction;)V"))return original;
                found[0]++;
                return new MethodVisitor(Opcodes.ASM9,original){@Override public void visitInsn(int opcode){
                    if(opcode==Opcodes.RETURN){super.visitVarInsn(Opcodes.ALOAD,0);super.visitMethodInsn(Opcodes.INVOKESTATIC,"unciv/civic/BulkPurchaseUI","attach","(Ljava/lang/Object;)V",false);}
                    super.visitInsn(opcode);
                }};
            }
        },0);
        if(found[0]!=1)throw new IllegalStateException("未匹配唯一按钮函数");active=true;return writer.toByteArray();
    }
}
