package codechicken.lib.render.shader;

import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.covers1624.quack.io.IOUtils;
import net.covers1624.quack.sort.TopologicalSort;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Similar to Mojang's own GLSL preprocessor.
 * However, more suited to a modded environment.
 * <p>
 * Created by covers1624 on 22/3/22.
 */
@SuppressWarnings ("ALL")
public class GlslProcessor {

    private static Logger LOGGER = LogManager.getLogger();

    private static final boolean DEBUG = Boolean.getBoolean("ccl.glsl_processor.debug");

    private static final Pattern VERSION_PATTERN = Pattern.compile("^#version (.*)$");
    // Matches #moj_import with <> or ""
    private static final Pattern IMPORT_PATTERN = Pattern.compile("^#moj_import (?>(?><(.*)>)|(?>\"(.*)\"))$");

    private static final Pattern NATIVE_INCLUDE=Pattern.compile("^\\s*#\\s*include\\s+<([^>]+)>\\s*(?://.*)?$");

    private final ResourceProvider resourceProvider;
    private final Identifier shader;

    public GlslProcessor(Identifier shader) {
        this(Minecraft.getInstance().getResourceManager(), shader);
    }

    public GlslProcessor(ResourceProvider resourceProvider, Identifier shader) {
        this.resourceProvider = resourceProvider;
        this.shader = shader;

    }

    public ProcessedShader process() {
        // Resolve the include DAG afresh for each invocation. DFS retains declared
        // sibling order, avoids duplicate includes and reports a cycle explicitly.
        var entries=new LinkedHashMap<Identifier,ProcessorEntry>();
        var order=new ArrayList<Identifier>();
        var visiting=new LinkedHashSet<Identifier>();
        visit(shader,entries,order,visiting);
        var mainEntry=entries.get(shader);
        String mainVersion=Objects.requireNonNull(mainEntry.version,"Main Shader '"+shader+"' requires #version.");
        List<String> outputLines=new ArrayList<>();
        var expandedOrder=new LinkedHashSet<Identifier>();
        if(!mainEntry.includes.isEmpty())outputLines.add("#version "+mainVersion);
        for(var id:order){
            var entry=entries.get(id);
            if(entry.version!=null&&!entry.version.equals(mainVersion))
                LOGGER.warn("Shader {} includes {} with version {}, expected {}",shader,id,entry.version,mainVersion);
            boolean legacy=!mainEntry.includes.isEmpty();
            if(legacy){outputLines.add("");outputLines.add("/*"+id+"*/");}
            appendNative(entry,entries,outputLines,expandedOrder,new LinkedHashSet<>(),legacy);
        }

        if (DEBUG) {
            Path out = Paths.get("glsl")
                    .resolve("assets")
                    .resolve(shader.getNamespace())
                    .resolve(shader.getPath());
            try {
                Files.write(IOUtils.makeParents(out), outputLines, StandardCharsets.UTF_8);
            } catch (IOException ex) {
                LOGGER.error("Failed to write debug glsl output to {}", out);
            }
        }

        return new ProcessedShader(
                shader,
                mainEntry.sourceName,
                List.copyOf(expandedOrder),
                String.join("\n", outputLines)
        );
    }

    /** Native angle-bracket includes are expanded in place, retaining #if/#extension order.
     * Unlike legacy hoisted imports, repeated native includes are not globally deduplicated:
     * conditional inclusion and header guards remain the GLSL preprocessor's responsibility.
     */
    private void appendNative(ProcessorEntry entry,Map<Identifier,ProcessorEntry> entries,List<String> output,
            Set<Identifier> order,Set<Identifier> active,boolean commentLegacy){
        if(!active.add(entry.shader))throw new IllegalArgumentException("Cyclic native shader includes: "+active+" -> "+entry.shader);
        boolean blockComment=false;
        for(int i=0;i<entry.lines.size();i++){
            String line=entry.lines.get(i);
            Matcher include=NATIVE_INCLUDE.matcher(line);
            if(!blockComment&&include.matches()){
                Identifier requested=Identifier.parse(include.group(1));
                String path=FilenameUtils.normalize("shaders/include/"+requested.getPath(),true);
                if(path==null||!path.startsWith("shaders/include/"))throw new IllegalArgumentException("Invalid shader include "+requested);
                Identifier id=Identifier.fromNamespaceAndPath(requested.getNamespace(),path);
                var child=entries.computeIfAbsent(id,ProcessorEntry::new);
                if(child.version!=null||!child.includes.isEmpty())
                    throw new IllegalArgumentException("Native include must be a header without #version or legacy #moj_import: "+id);
                output.add("// begin include "+id);
                appendNative(child,entries,output,order,active,false);
                output.add("// end include "+id);
            }else{
                output.add(commentLegacy&&entry.linesToComment.contains(i)?"/*"+line+"*/":line);
                // Ignore directives inside multiline comments.
                for(int c=0;c+1<line.length();c++){
                    if(blockComment&&line.startsWith("*/",c)){blockComment=false;c++;}
                    else if(!blockComment&&line.startsWith("//",c))break;
                    else if(!blockComment&&line.startsWith("/*",c)){blockComment=true;c++;}
                }
            }
        }
        active.remove(entry.shader);order.add(entry.shader);
    }

    private void visit(Identifier id,Map<Identifier,ProcessorEntry> entries,List<Identifier> order,Set<Identifier> visiting){
        if(visiting.contains(id))throw new IllegalArgumentException("Cyclic shader imports: "+visiting+" -> "+id);
        if(entries.containsKey(id))return;
        visiting.add(id);
        var entry=new ProcessorEntry(id);
        entries.put(id,entry);
        for(var include:entry.includes)visit(include,entries,order,visiting);
        visiting.remove(id);
        order.add(id);
    }

    public static record ProcessedShader(
            Identifier shader,
            String sourceName,
            List<Identifier> order,
            String processedSource) {
    }

    private class ProcessorEntry {

        private final Identifier shader;
        private final String sourceName;
        private final List<String> lines;
        private final List<Identifier> includes;
        @Nullable
        private final String version;
        private final IntSet linesToComment;

        private ProcessorEntry(Identifier shader) {
            this.shader = shader;
            try {
                Resource resource = resourceProvider.getResourceOrThrow(shader);
                sourceName = resource.sourcePackId();
                try (BufferedReader reader = resource.openAsReader()) {
                    lines = reader.lines().toList();
                }
            } catch (IOException ex) {
                throw new RuntimeException("Unable to read asset '" + shader + "'.", ex);
            }
            linesToComment = new IntOpenHashSet();
            includes = extractIncludes(lines, linesToComment);
            version = extractVersion(lines, linesToComment);
        }

        private static List<Identifier> extractIncludes(List<String> lines, IntSet linesToComment) {
            List<Identifier> imports = new LinkedList<>();
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                Matcher matcher = IMPORT_PATTERN.matcher(line);
                if (matcher.find()) {
                    linesToComment.add(i);
                    String match = matcher.group(1);
                    boolean includeFolder = match != null;
                    if (!includeFolder) {
                        match = matcher.group(2);
                    }
                    Identifier loc = Identifier.parse(match);
                    if (includeFolder) {
                        loc = Identifier.fromNamespaceAndPath(loc.getNamespace(), FilenameUtils.normalize("shaders/include/" + loc.getPath(), true));
                    }
                    imports.add(loc);
                }
            }

            return List.copyOf(imports);
        }

        @Nullable
        private static String extractVersion(List<String> lines, IntSet linesToComment) {
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                Matcher matcher = VERSION_PATTERN.matcher(line);
                if (matcher.find()) {
                    linesToComment.add(i);
                    return matcher.group(1);
                }
            }
            return null;
        }
    }

}
