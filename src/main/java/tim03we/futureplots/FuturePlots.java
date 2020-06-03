package tim03we.futureplots;

/*
 * This software is distributed under "GNU General Public License v3.0".
 * This license allows you to use it and/or modify it but you are not at
 * all allowed to sell this plugin at any cost. If found doing so the
 * necessary action required would be taken.
 *
 * GunGame is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License v3.0 for more details.
 *
 * You should have received a copy of the GNU General Public License v3.0
 * along with this program. If not, see
 * <https://opensource.org/licenses/GPL-3.0>.
 */

import cn.nukkit.Player;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import cn.nukkit.level.generator.Generator;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.plugin.PluginManager;
import tim03we.futureplots.commands.*;
import tim03we.futureplots.generator.PlotGenerator;
import tim03we.futureplots.handler.CommandHandler;
import tim03we.futureplots.listener.*;
import tim03we.futureplots.provider.*;
import tim03we.futureplots.tasks.PlotClearTask;
import tim03we.futureplots.utils.Language;
import tim03we.futureplots.utils.Plot;
import tim03we.futureplots.utils.PlotSettings;
import tim03we.futureplots.utils.Settings;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class FuturePlots extends PluginBase {

    private final HashMap<String, Class<?>> providerClass = new HashMap<>();

    private static FuturePlots instance;
    public static EconomyProvider economyProvider;
    public static DataProvider provider;

    @Override
    public void onLoad() {
        instance = this;
        saveDefaultConfig();
        providerClass.put("yaml", YamlProvider.class);
        registerGenerator();
    }

    @Override
    public void onEnable() {
        new File(getDataFolder() + "/worlds/").mkdirs();
        registerCommands();
        registerEvents();
        Settings.init();
        Language.init();
        loadWorlds();
        initProvider();
        checkVersion();
    }

    private void registerEvents() {
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new BlockBreak(), this);
        pm.registerEvents(new BlockPiston(), this);
        pm.registerEvents(new BlockPlace(), this);
        pm.registerEvents(new EntityExplode(), this);
        pm.registerEvents(new EntityShootBow(), this);
        pm.registerEvents(new LiquidFlow(), this);
        pm.registerEvents(new PlayerInteract(), this);
        pm.registerEvents(new PlayerMove(), this);
    }

    private void checkVersion() {
        if(!Language.getNoPrefix("version").equals("1.2.2")) {
            new File(getDataFolder() + "/lang/" + Settings.language + "_old.yml").delete();
            if(new File(getDataFolder() + "/lang/" + Settings.language + ".yml").renameTo(new File(getDataFolder() + "/lang/" + Settings.language + "_old.yml"))) {
                getLogger().critical("The version of the language configuration does not match. You will find the old file marked \"" + Settings.language + "_old.yml\" in the same language directory.");
                Language.init();
            }
        }
        if(!getConfig().getString("version").equals("1.1.0")) {
            new File(getDataFolder() + "/config_old.yml").delete();
            if(new File(getDataFolder() + "/config.yml").renameTo(new File(getDataFolder() + "/config_old.yml"))) {
                getLogger().critical("The version of the configuration does not match. You will find the old file marked \"config_old.yml\" in the same directory.");
                saveDefaultConfig();
            }
        }
    }

    @Override
    public void onDisable() {
        provider.save();
    }

    private void initProvider() {
        Class<?> providerClass = this.providerClass.get((this.getConfig().get(Settings.provider, "yaml")).toLowerCase());
        if (providerClass == null) { this.getLogger().critical("The specified provider could not be found."); }
        try { this.provider = (DataProvider) providerClass.newInstance();
        } catch (InstantiationException | IllegalAccessException e) { this.getLogger().critical("The specified provider could not be found.");
            getServer().getPluginManager().disablePlugin(getServer().getPluginManager().getPlugin("FuturePlots"));
            return;
        }
        if(Settings.economy) {
            try {
                if(getServer().getPluginManager().getPlugin("EconomyAPI") != null) {
                    economyProvider = EconomySProvider.class.newInstance();
                    getLogger().warning("Economy provider was set to EconomyS.");
                } else {
                    Settings.economy = false;
                    getLogger().critical("A Economy provider could not be found.");
                    getLogger().critical("The Economy function has been deactivated.");
                }
            } catch (InstantiationException | IllegalAccessException e) {
                Settings.economy = false;
                getLogger().critical("A Economy provider could not be found.");
                getLogger().critical("The Economy function has been deactivated.");
            }
        }
    }

    private void registerCommands() {
        CommandHandler commandHandler = new CommandHandler();
        commandHandler.registerCommand("청소", new ClearCommand("청소", "땅을 처음 상태로 초기화 합니다.", "/땅 청소"), new String[]{});
        commandHandler.registerCommand("포기", new DeleteCommand("포기", "땅의 소유권을 포기합니다.", "/땅 포기"), new String[]{});
        commandHandler.registerCommand("구매", new ClaimCommand("구매", "밟고 있는 땅을 구매합니다.", "/땅 구매"), new String[]{});
        commandHandler.registerCommand("이동", new HomeCommand("이동", "땅으로 이동합니다.", "/땅 이동"), new String[]{});
        commandHandler.registerCommand("목록", new HomesCommand("목록", "소유하고 있는 땅의 목록을 불러옵니다.", "/땅 목록"), new String[]{});
        commandHandler.registerCommand("도움말", new HelpCommand("도움말", "땅의 도움말 입니다.", "/땅 도움말"), new String[]{});
        commandHandler.registerCommand("생성", new GenerateCommand("생성", "땅 월드를 생성합니다.", "/땅 생성"), new String[]{});
        commandHandler.registerCommand("자동", new AutoCommand("자동", "자동으로 빈 땅을 구매합니다.", "/땅 자동"), new String[]{});
        commandHandler.registerCommand("정보", new InfoCommand("정보", "밟고 있는 땅의 정보입니다.", "/땅 정보"), new String[]{});
        commandHandler.registerCommand("추방", new DenyCommand("추방", "해당 플레이어의 땅 출입을 제한합니다.", "/땅 추방 <이름>"), new String[]{});
        commandHandler.registerCommand("허용", new UnDenyCommand("허용", "해당 플레이어의 땅 출입을 허용합니다.", "/땅 허용 <이름>"), new String[]{});
        commandHandler.registerCommand("도우미추가", new AddHelperCommand("도우미추가", "해당 플레이어를 도우미로 추가합니다.", "/땅 도우미 <이름>"), new String[]{});
        commandHandler.registerCommand("도우미제거", new RemoveHelperCommand("도우미제거", "해당 플레이어를 도우미에서 제거합니다.", "/땅 도우미제거 <이름>"), new String[]{});
        commandHandler.registerCommand("구성원추가", new AddMemberCommand("구성원추가", "땅의 구성원을 추가합니다.", "/땅 구성원추가 <이름>"), new String[]{});
        commandHandler.registerCommand("구성원제거", new RemoveMemberCommand("구성원제거", "땅의 구성원을 제거합니다.", "/땅 구성원제거 <이름>"), new String[]{});
        commandHandler.registerCommand("내놓기", new DisposeCommand("내놓기", "땅을 초기화 하지 않고 내놓습니다.", "/땅 내놓기"), new String[]{});
        FuturePlots.getInstance().getServer().getCommandMap().register("plots", new MainCommand());
        /* ToDo */
        //commandHandler.registerCommand("flag", new FlagCommand("flag", " , "/plot flag <flag> [value]"), new String[]{});
        /* ToDo */
    }

    public static FuturePlots getInstance() {
        return instance;
    }


    private void registerGenerator() {
        Generator.addGenerator(PlotGenerator.class, "futureplots", Generator.TYPE_INFINITE);
    }

    private void loadWorlds() {
        for (String world : Settings.levels) {
            new PlotSettings(world).initWorld();
            getServer().loadLevel(world);
        }
    }

    public void generateLevel(String levelName) {
        Settings.levels.add(levelName);
        new PlotSettings(levelName).initWorld();
        Map<String, Object> options = new HashMap<>();
        options.put("preset", levelName);
        getServer().generateLevel(levelName, 0, Generator.getGenerator("futureplots"), options);
    }

    public void clearPlot(Plot plot) {
        getServer().getScheduler().scheduleDelayedTask(this, new PlotClearTask(plot), 1, true);
    }

    public int claimAvailable(Player player) {
        if (player.isOp()) return -1;
        int max_plots = Settings.max_plots;
        for (String perm : player.getEffectivePermissions().keySet()) {
            if (perm.contains("futureplots.plot.")) {
                String max = perm.replace("futureplots.plot.", "");
                if (max.equalsIgnoreCase("unlimited")) {
                    return -1;
                } else {
                    try {
                        int num = Integer.parseInt(max);
                        if (num > max_plots) max_plots = num;
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        return max_plots;
    }

    public Position getPlotPosition(Plot plot) {
        int plotSize = new PlotSettings(plot.getLevelName()).getPlotSize();
        int roadWidth = new PlotSettings(plot.getLevelName()).getRoadWidth();
        int totalSize = plotSize + roadWidth;
        int x = totalSize * plot.getX();
        int z = totalSize * plot.getZ();
        Level level = getServer().getLevelByName(plot.getLevelName());
        return new Position(x, Settings.groundHeight, z, level);
    }

    public Position getPlotBorderPosition(Plot plot) {
        int plotSize = new PlotSettings(plot.getLevelName()).getPlotSize();
        int roadWidth = new PlotSettings(plot.getLevelName()).getRoadWidth();
        int totalSize = plotSize + roadWidth;
        int x = totalSize * plot.getX();
        int z = totalSize * plot.getZ();
        Level level = getServer().getLevelByName(plot.getLevelName());
        return new Position(x += Math.floor(new PlotSettings(plot.getLevelName()).getPlotSize() / 2), Settings.groundHeight += 1.5, z -= 1, level);
    }


    public Plot getPlotByPosition(Position position) {
        double x = position.x;
        double z = position.z;
        int X;
        int Z;
        double difX;
        double difZ;
        int plotSize = new PlotSettings(position.getLevel().getName()).getPlotSize();
        int roadWidth = new PlotSettings(position.getLevel().getName()).getRoadWidth();
        int totalSize = plotSize + roadWidth;
        if(x >= 0) {
            X = (int) Math.floor(x / totalSize);
            difX = x % totalSize;
        }else{
            X = (int) Math.ceil((x - plotSize + 1) / totalSize);
            difX = Math.abs((x - plotSize + 1) % totalSize);
        }
        if(z >= 0) {
            Z = (int) Math.floor(z / totalSize);
            difZ = z % totalSize;
        }else {
            Z = (int) Math.ceil((z - plotSize + 1) / totalSize);
            difZ = Math.abs((z - plotSize + 1) % totalSize);
        }
        if((difX > plotSize - 1) || (difZ > plotSize - 1)) {
            return null;
        }
        return new Plot(X, Z, position.getLevel().getName());
    }

    public String findEmptyPlotSquared(int a, int b, ArrayList<String> plots) {
        if (!plots.contains(a + ";" + b)) return a + ";" + b;
        if(!plots.contains(b + ";" + a)) return b + ";" + a;
        if(a != 0) {
            if(!plots.contains(-a + ";" + b)) return -a + ";" + b;
            if(!plots.contains(b + ";" + -a)) return b + ";" + -a;
        }
        if(b != 0) {
            if(!plots.contains(-b + ";" + a)) return -b + ";" + a;
            if(!plots.contains(a + ";" + -b)) return a + ";" + -b;
        }
        if(a == 0 | b == 0) {
            if(!plots.contains(-a + ";" + -b)) return -a + ";" + -b;
            if(!plots.contains(-b + ";" + -a)) return -b + ";" + -a;
        }
        return null;
    }
}
