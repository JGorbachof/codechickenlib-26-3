package codechicken.lib.render.shader;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ShaderMigrationTests {
    @Test void uniformCacheOwnsValuesAndRejectsInvalidCarrier(){
        var uniform=CCUniform.makeUniform("x",UniformType.VEC2,2);
        float[] values={1,2};
        uniform.glUniformF(false,values);
        values[0]=99;
        assertEquals(1F,uniform.getFloatBuffer().get(0));
        assertThrows(IllegalArgumentException.class,()->uniform.glUniformF(true,1,2));
        assertThrows(IllegalArgumentException.class,()->uniform.glUniformI(1,2));
        assertThrows(IllegalArgumentException.class,()->uniform.glUniformF(false,1));
        uniform.setSafe(3F,4F,5F,6F);
        assertEquals(4F,uniform.getFloatBuffer().get(1));
    }
    @Test void externalShaderUniformsAreCollectedAndConflictsRejected(){
        var shader=new SimpleShaderObject("vertex",ShaderObject.StandardShaderType.VERTEX,List.of(new UniformPair("scale",UniformType.FLOAT)),"source");
        var builder=ShaderProgramBuilder.builder().addShader(shader);
        assertEquals(UniformType.FLOAT,builder.build().getUniform("scale").getUniformType());
        var conflicting=new SimpleShaderObject("fragment",ShaderObject.StandardShaderType.FRAGMENT,List.of(new UniformPair("scale",UniformType.INT)),"source");
        assertThrows(IllegalArgumentException.class,()->builder.addShader(conflicting));
    }
    @Test void explicitZeroSpecializationOverridesShaderDefault(){
        var cache=new ShaderConstantCache();
        cache.constant1i(1,0);
        cache.constant1b(2,false);
        cache.constant1f(3,-0F);
        assertArrayEquals(new int[]{1,2,3},cache.getIndices());
        assertArrayEquals(new int[]{0,0,Float.floatToRawIntBits(-0F)},cache.getValues());
    }
}