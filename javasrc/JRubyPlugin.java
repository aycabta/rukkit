package com.supermomonga.rukkit;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.stream.Stream;
import java.util.stream.Collectors;
import java.net.URL;
import java.net.URI;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.net.URLDecoder;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Event;
import org.bukkit.configuration.file.FileConfiguration;
import org.jruby.RubyObject;
import org.jruby.embed.ScriptingContainer;
import org.jruby.embed.EvalFailedException;


public class JRubyPlugin extends JavaPlugin implements Listener {
  private ScriptingContainer jruby;
  private HashMap<String, Object> eventHandlers = new HashMap<String, Object>();
  private Object rubyTrue, rubyFalse, rubyNil, rubyModule;
  private FileConfiguration config;

  private void initializeJRuby() {
    jruby = new ScriptingContainer();
    jruby.setClassLoader(getClass().getClassLoader());
    jruby.setCompatVersion(org.jruby.CompatVersion.RUBY2_0);

    // Because of no compatibility with Java's one
    rubyTrue = jruby.runScriptlet("true");
    rubyFalse = jruby.runScriptlet("false");
    rubyNil = jruby.runScriptlet("nil");
    rubyModule = jruby.runScriptlet("Module");
  }

  private boolean isRubyMethodExists(Object eventHandler, String method) {
    if (jruby.callMethod(eventHandler, "respond_to?", method).equals(rubyTrue)) {
      return true;
    } else {
      return false;
    }
  }

  private void callJRubyMethodIfExists(String method, Object arg1) {
    for (Object eventHandler : eventHandlers.values())
      if (isRubyMethodExists(eventHandler, method))
        jruby.callMethod(eventHandler, method, arg1);
  }

  private void callJRubyMethodIfExists(String method, Object arg1, Object arg2) {
    for (Object eventHandler : eventHandlers.values())
      if (isRubyMethodExists(eventHandler, method))
        jruby.callMethod(eventHandler, method, arg1, arg2);
  }

  private void callJRubyMethodIfExists(String method, Object arg1, Object arg2, Object arg3) {
    for (Object eventHandler : eventHandlers.values())
      if (isRubyMethodExists(eventHandler, method))
        jruby.callMethod(eventHandler, method, arg1, arg2, arg3);
  }

  private void callJRubyMethodIfExists(String method, Object arg1, Object arg2, Object arg3, Object arg4) {
    for (Object eventHandler : eventHandlers.values())
      if (isRubyMethodExists(eventHandler, method))
        jruby.callMethod(eventHandler, method, arg1, arg2, arg3, arg4);
  }


  private void loadConfig() {
    config = getConfig();
  }

  private Object evalRuby(String script) {
    try {
      return jruby.runScriptlet(script);
    } catch (EvalFailedException e) {
      return rubyNil;
    } finally {
      return rubyNil;
    }
  }

  private Object loadJRubyScript(InputStream io, String path) {
    try {
      return jruby.runScriptlet(io, path);
    } finally {
      try {
        if (io != null) {
          io.close();
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  private void loadRukkitBundledScript(String script) {
    getLogger().info("Loading script: [" + script + "]");
    InputStream is = null;
    BufferedReader br = null;
    try {
      is = this.getClass().getClassLoader().getResource("scripts/" + script + ".rb").openStream();
      br = new BufferedReader(new InputStreamReader(is));

      String scriptBuffer =
        br.lines().collect(Collectors.joining("\n"));

      RubyObject eventHandler = (RubyObject)jruby.runScriptlet(scriptBuffer);
      getLogger().info("Script loaded: [" + script + "]");

    } catch (Exception e) {
      getLogger().info("Failed to load script: [" + script + "]");
      e.printStackTrace();
    } finally {
      if (is != null) try { is.close(); } catch (IOException e) {}
      if (br != null) try { br.close(); } catch (IOException e) {}
    }
  }

  private void loadRukkitBundledScripts(List<String> scripts) {
    for (String script : scripts) {
      loadRukkitBundledScript(script);
    }
  }

  private void loadRukkitScript(String scriptDir, String script) {
    getLogger().info("Loading script: [" + script + "]");
    String scriptPath = scriptDir + script + ".rb";
    try {
      // Define module
      String moduleName = snakeToCamel(script);
      String scriptBuffer =
        Files.readAllLines(Paths.get(scriptPath)).stream().collect(Collectors.joining("\n"));
      jruby.runScriptlet(scriptBuffer);
      getLogger().info("Script loaded: [" + script + "]");
    } catch (Exception e) {
      getLogger().info("Failed to load script: [" + script + "]");
      e.printStackTrace();
    }
  }

  private void loadRukkitScripts(String scriptDir, List<String> scripts) {
    for (String script : scripts) {
      loadRukkitScript(scriptDir, script);
    }
  }

  private void loadRukkitPlugin(String pluginDir, String plugin) {
    getLogger().info("Loading plugin: [" + plugin + "]");
    String pluginPath = pluginDir + plugin + ".rb";
    try {
      String moduleName = snakeToCamel(plugin);

      // Add script dir to $LOAD_PATH automatically
      String userScriptsPath = config.getString("rukkit.script_dir");
      String loadPathStatement = "";
      if (userScriptsPath != null)
        loadPathStatement = "$LOAD_PATH << '" + userScriptsPath + "'\n";

      // Add resource ruby loader
      String resourceLoader =
        "import 'com.supermomonga.rukkit.Loader'" +
        "def require_resource(name)" +
        "  buffer = Loader.new.get_resource_as_string %`#{name}.rb`" +
        "  eval buffer unless buffer.nil?" +
        "end"

      String pluginBuffer =
        "# encoding: utf-8\n"
        + loadPathStatement
        + resourceLoader
        + Files.readAllLines(Paths.get(pluginPath)).stream().collect(Collectors.joining("\n"))
        + "\n"
        + "nil.tap{\n"
        +   "break " + moduleName + " if defined? " + moduleName + "\n"
        + "}";
      RubyObject eventHandler = (RubyObject)jruby.runScriptlet(pluginBuffer);

      // Add Module to event handler list
      if (eventHandler != rubyNil && eventHandler.getType() == rubyModule) {
        eventHandlers.put(plugin, eventHandler);
        getLogger().info("Plugin loaded: [" + plugin + "]");
      } else {
        getLogger().info("Plugin loaded but module not defined: [" + plugin + "]");
      }
    } catch (Exception e) {
      getLogger().info("Failed to load plugin: [" + plugin + "]");
      e.printStackTrace();
    }
  }

  private void loadRukkitPlugins(String pluginDir, List<String> plugins) {
    for (String plugin : plugins) {
      loadRukkitPlugin(pluginDir, plugin);
    }
  }

  private boolean isModuleDefined(String moduleName) {
    return isDefined(moduleName, "constant");
  }

  private boolean isDefined(String objectName, String type) {
    return type.equals(jruby.runScriptlet("defined? " + objectName));
  }

  private String snakeToCamel(String snake) {
    return Arrays.asList(snake.split("_")).stream().map(
        w -> w.substring(0,1).toUpperCase() + w.substring(1)
        ).collect(Collectors.joining(""));
  }

  private void loadCoreScripts() {
    List<String> scripts = new ArrayList<String>();
    scripts.add("util");

    loadRukkitBundledScripts( scripts );
  }

  private void loadUserScripts() {
    if (config.getString("rukkit.script_dir") != null &&
        config.getStringList("rukkit.scripts") != null)
      loadRukkitScripts(
          config.getString("rukkit.script_dir"),
          config.getStringList("rukkit.scripts")
          );
  }

  private void loadUserPlugins() {
    if (config.getString("rukkit.plugin_dir") != null &&
        config.getStringList("rukkit.plugins") != null)
      loadRukkitPlugins(
          config.getString("rukkit.plugin_dir"),
          config.getStringList("rukkit.plugins")
          );
  }

  private void applyEventHandler() {
    getServer().getPluginManager().registerEvents(this, this);
  }

  @Override
  public void onEnable() {
    initializeJRuby();
    loadConfig();

    loadCoreScripts();
    loadUserScripts();
    loadUserPlugins();
    getLogger().info("Rukkit enabled!");

    applyEventHandler();
  }

  @Override
  public void onDisable() {
    getLogger().info("Rukkit disabled!");
  }

  @Override
  public boolean onCommand( org.bukkit.command.CommandSender sender, org.bukkit.command.Command command, String label, String[] args ) {
    getLogger().info("Command passed!");
    return true;
  }

  // EventHandler mappings
  // TODO: I want to generate all event handler mappings automatically,
  //       but it must be painful to parse JavaDoc...
  //       @ujm says that "use jruby repl and ruby reflection to list them up."
  @EventHandler
  public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent event) {
    getLogger().info("eh: on_player_join");
    callJRubyMethodIfExists("on_player_join", event);
  }
  @EventHandler
  public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
    getLogger().info("eh: on_player_quit");
    callJRubyMethodIfExists("on_player_quit", event);
  }
  @EventHandler
  public void onPlayerToggleSprint(org.bukkit.event.player.PlayerToggleSprintEvent event) {
    getLogger().info("eh: on_player_toggle_sprint");
    callJRubyMethodIfExists("on_player_toggle_sprint", event);
  }
}
