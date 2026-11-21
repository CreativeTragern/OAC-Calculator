import dev.oac.core.OacMath;
import java.util.Map;
public class MathProbe {
    public static void main(String[] args) {
        for (String arg : args) {
            String[] parts = arg.split(",");
            try {
                Map<String,Object> r = OacMath.calculate(true, 15,
                    Double.parseDouble(parts[0]), Double.parseDouble(parts[1]));
                System.out.println(arg + "\t" + r);
            } catch (Exception e) {
                System.out.println(arg + "\tERROR:" + e.getMessage());
            }
        }
    }
}
