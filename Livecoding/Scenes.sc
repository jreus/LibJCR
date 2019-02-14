/******************************************
Livecoding Scenography

(C) 2018 Jonathan Reus
GPL

*******************************************/

/*--------------------------------------
Todo:
>>> A hierarchical scene system for livecoding
>> different layers, a "meta scene" that encompasses all scenes.. things like server booting and that kind of thing, or recordings... and sub-scenes for livecoding or interface designs
>> be able to manage "template" files that are copied as instances
>> use a macro or use a UI for navigation

>^>^> what about file-based macros, to accompany XML-based macros?
>>>> this would make transforming a quick piece of code into a macro much easier!
>>> I could also consider using the Scene editor as an interface for editing & using macros..

>> hmm, the question is for what purpose / why would I want heirarchies of scenes?
--------------------------------------*/


/*_____________________________________________________________
@class
Scenes
A scene system for livecoding. Template files are stored in a
"scenes" directory, and instances are created from those templates
that can be performed and modified at will without transforming the
original.

Works a bit like Macros but on the scale of files. Keeps track of all your
instances within a performance concept.


@usage

Create a file called root.scd where your root scene will be located.
From inside the root.scd call
~sc = Scenes.new;

f = "".resolveRelative +/+ "scenes";
z = Scenes(f).makeGui;
________________________________________________________________*/

Scenes {
  classvar <rootPath, <scenePath, <instancePath, <sceneNames;
  classvar <win, <onSelectOption=0;
  classvar <meters,<initialized=false;


  *meter {|server|
    var bnd, len;
    server = server ? Server.default;
    if(meters.isNil.or { meters.window.isClosed }) {
      meters = server.meter;
      meters.window.alwaysOnTop_(true).front;
      bnd = meters.window.bounds;
      len = Window.screenBounds.width - bnd.width;
      meters.window.bounds = Rect(len, 0, bnd.width, bnd.height);
    } {
      meters.window.front;
    };
    ^meters;
  }

  // should throw a fatal error if not being run from root.scd
  *init {|rootpath, scenedir|
    var thispath;
    if(rootpath.isNil) {
      rootpath = PathName(Document.current.path);
    } {
      rootpath = PathName(rootpath +/+ "root.scd");
    };

    if(rootpath.isFile.not.or {rootpath.fileName != "root.scd"}) {
        "Scenes must be initialized from 'root.scd', or a path to 'root.scd' must be provided".throw;
    };
    rootPath = rootpath.pathOnly;
    if(scenedir.isNil) { scenedir = rootPath +/+ "_scenes/" };
    scenePath = scenedir;
    "Scenes root: %\nScenes dir: %\n".format(rootPath, scenePath).postln;

    if(File.exists(scenePath).not) {
      File.mkdir(scenePath);
    };
    if(File.exists(rootPath +/+ "root.scd").not) {
      File.use(rootPath +/+ "root.scd","w", {|fp|
        fp.write("/*** Root Scene ***/\nMacros.load\n~sc=Scene.new\n")
      });
    };
    sceneNames = (scenePath +/+ "*.scd").pathMatch.collect {|it|
      PathName(it).fileNameWithoutExtension
    };
    sceneNames = sceneNames.insert(0, "root"); // root is a reserved scene name
    instancePath = scenePath +/+ "instances/";
    if(File.exists(instancePath).not) { File.mkdir(instancePath) };
    initialized=true;
  }

  *startup {|server=nil, showScenes=true, showMeters=true, loadSamples=true, limitSamplesLocal=1000, limitSamplesGlobal=30000, rootpath=nil, scenedir=nil, onBoot=nil|
    var win;
    this.init(rootpath, scenedir);

    // Choose Audio Device, Boot Server, Load Macros, Synths & Samples
    if(server.isNil) { server = Server.default };
    // TODO: Also choose block size in device selector
    win = Window.new("Choose Audio Device", Rect(400,500,200,200));
    ListView.new(win, Rect(0, 0, 200, 200))
    .items_(ServerOptions.devices.sort)
    .mouseUpAction_({|lv|
      "Using %\n".postf(lv.items[lv.value]);
      server.options.device = lv.items[lv.value];
      server.options.numInputBusChannels = 20;
      server.options.numOutputBusChannels = 20;
      server.options.memSize = 65536;
      server.options.blockSize = 64;
      server.options.numWireBufs = 512;
      win.close;
      server.waitForBoot {
        var macrodir;
        if(showMeters) {
        var bnd, len;
        if(meters.notNil) { meters.window.close };
        meters = server.meter;
        meters.window.alwaysOnTop_(true).front;
        bnd = meters.window.bounds;
        len = Window.screenBounds.width - bnd.width;
        meters.window.bounds = Rect(len, 0, bnd.width, bnd.height);
        };

        Syn.load;
        Macros.load(rootPath +/+ "_macros/");
        if(loadSamples) {
            Smpl.load(server,
              localsampledir: rootPath +/+ "_samples/",
              verbose: false,
              limitLocal: limitSamplesLocal,
              limitGlobal: limitSamplesGlobal,
              doneFunc: { if(showScenes) { this.gui } });
        };

        if(onBoot.notNil) { onBoot.value };
      };
      });
    win.alwaysOnTop_(true).front;
  }

  *sceneExists {|name|
    ^(sceneNames.notNil.and { sceneNames.any({|sc| sc==name}) });
  }

  *getInstancePathsForScene {|name|
    if(this.sceneExists(name)) {
      ^("%/*%.scd".format(instancePath, name).pathMatch);
    };
    ^nil;
  }

  *getInstanceNamesForScene {|name|
    if(this.sceneExists(name)) {
      ^("%/*%.scd".format(instancePath, name)
        .pathMatch.sort.reverse
        .collect {|path| PathName(path).fileName[..12] });
    };
    ^nil;
  }

  *gui {|position|
    var width = 200, height = 600, lineheight=20, top=0, left=0;
    var styler, decorator, childView;
    var sceneList, searchField;
    var addBtn, deleteBtn, renameBtn;
    var subView, subStyler;
    var lastClick = Process.elapsedTime;

    if(win.notNil) {
      if(win.isClosed.not) {
        win.front;
        ^win;
      };
    };
    if(position.notNil) {
      top = position.y; left = position.x;
    };
    win = Window("Scene Navigator", Rect(left, top, (width+10), height));
    styler = GUIStyler(win);

    // child view inside window, this is where all the gui views sit
    childView = styler.getView("Scenes", win.view.bounds, gap: 10@10);

    addBtn = styler.getSizableButton(childView, "add", size: 30@lineheight);
    deleteBtn = styler.getSizableButton(childView, "del", size: 30@lineheight);
    renameBtn = styler.getSizableButton(childView, "rename", size: 40@lineheight);

    sceneList = ListView(childView, width@(height/2))
    .items_(sceneNames.asArray).value_(nil)
    .stringColor_(Color.white).background_(Color.clear)
    .hiliteColor_(Color.new(0.3765, 0.5922, 1.0000, 0.5));

    addBtn.action = {|btn|
      var idx, newscene;
      styler.makeModalTextEntryDialog("Add Scene", "",
        "new_scene", "Add", "Cancel",
        yesAction:{|txt|
          if(txt == "root") {
            "'root' is a reserved scene name".error;
          } {
            var idx;
            idx = sceneList.items.indexOfEqual(txt);
            if(idx.notNil) {//a scene with that name exists
              "FAILURE: A scene named % already exists".format(txt).warn;
            } { // create a new scene
              var path = scenePath +/+ txt ++ ".scd";
              File.use(path, "w", {|fp|
                fp.write("/* Scene % */".format(txt));
                "Scn: created new scene template at %".format(path).postln;
              });
              sceneList.items = sceneList.items.add(txt);
            };
          };
        },
        noAction:{|txt| "Abort Add".postln });
    };

    deleteBtn.action = {|btn|
      var dialog,tmp,bounds,warning,scene,scenepath;
      scene = sceneList.items[sceneList.value];
      if(scene == "root") {
        "'root' cannot be deleted".error;
      } {
        // TODO: need to close scene windows if they are open
        scenepath = scenePath +/+ scene ++ ".scd";
        warning = "Delete '%'\nAre you sure?".format(scene);
        styler.makeModalConfirmDialog(
          "Confirm Delete",
          warning, "Yes", "Cancel",
          yesAction: {
            var newitems;
            newitems = sceneList.items.copy;
            newitems.removeAt(sceneList.value);
            sceneList.items = newitems;
            File.delete(scenepath);
            "Delete % at %".format(scene, scenepath).postln;
            // TODO:: Don't delete, instead move template & any instances to the bin
          },
          noAction: {
            "Abort Delete".postln;
        });

      };
    };

    renameBtn.action = {|btn|
      var scene, msg;
      scene = sceneList.items[sceneList.value];
      if(scene == "root") {
        "'root' cannot be renamed".error;
      } {
        msg = "Enter a new name for '%'".format(scene);
        styler.makeModalTextEntryDialog("Rename Scene",
          msg, scene,
          "Rename", "Cancel",
          yesAction: {|newname|
            var newlist, oldpath, newpath, oldinstancepaths, newinstancepaths;
            oldpath = scenePath +/+ scene ++ ".scd";
            newpath = scenePath +/+ newname ++ ".scd";
            if(File.exists(newpath)) {
              "a scene named '%' already exists".format(newname).error;
            } {
              // TODO: need to close old scene windows if they are open
              oldinstancepaths = this.getInstancePathsForScene(scene);
              newinstancepaths = oldinstancepaths.collect {|path|
                path.replace(scene ++ ".scd", newname ++ ".scd");
              };

              // rename template
              File.copy(oldpath, newpath);
              File.delete(oldpath);

              // rename/move instances
              oldinstancepaths.do {|oldpath, idx|
                File.copy(oldpath, newinstancepaths[idx]);
                File.delete(oldpath);
              };

              "Scn: scene % renamed to % at %".format(scene,newname,newpath).postln;

              // refresh list
              newlist = sceneList.items.replace([scene],[newname]);
              sceneList.items = newlist;
            };
          },
          noAction: {|newname| "Scn: abort rename".postln });
      };

    };


    subStyler = GUIStyler(childView); // styler for subwindow
    subView = subStyler.getView("Subwindow", Rect(0,0,width,height/2)); // subwindow


    // SCENELIST DoubleClick Action
    sceneList.mouseUpAction = {
      var templatepath, scene, thresh = 0.3, now = Process.elapsedTime;
      if((now - lastClick) < thresh) {
        // Open Source
        scene = sceneList.items[sceneList.value];
        if(scene == "root") {
          templatepath = rootPath +/+ "root.scd";
        } {
          templatepath = scenePath +/+ scene ++ ".scd";
        };
        Document.open(templatepath);
      };
      lastClick = now;
    };

    // SCENELIST ACTION (on selection)
    sceneList.action_({ |lv|
      var btn, radio, instanceList, loadInstanceFunc, newInstanceFunc;
      var matching, scene, templatepath;
      scene = lv.items[lv.value];
      if(scene == "root") {
        templatepath = rootPath +/+ "root.scd";
      } {
        templatepath = scenePath +/+ scene ++ ".scd";
      };
      "select % %".format(scene,templatepath).postln;

      // *** BUILD SCENE INFO WINDOW ***
      subView.removeAll; // remove views & layout for previous scene info window
      subView.decorator = FlowLayout(subView.bounds);

      newInstanceFunc = {|sceneName|
        var res, instances;
        res = instancePath +/+ Date.getDate.stamp ++ "_" ++ sceneName ++ ".scd";
        File.use(res, "w", {|fp| fp.write(File.readAllString(scenePath +/+ sceneName ++ ".scd")) });
        instances = this.getInstanceNamesForScene(sceneName);
        instanceList.items_(instances).refresh;
        res;
      };

      loadInstanceFunc = {|option=0|
        var allInstancePaths, lastInstancePath=nil, lastInstanceDate, newInstancePath=nil;
        allInstancePaths = (instancePath+/+"*%.scd".format(scene)).pathMatch.sort;
        if(allInstancePaths.size > 0) {
          lastInstancePath = allInstancePaths.last;
          lastInstanceDate = Date.fromStamp(PathName(lastInstancePath).fileName[..12]);
        };

        // option 0 do nothing

        if(option == 1) {// load last instance, if no instance exists, create one
          if(lastInstancePath.isNil) {
            Document.open(newInstanceFunc.(scene));
          } {
            Document.open(lastInstancePath);
          };
        };
        if(option == 2) {// create new instance if no "recent" instance already exists
          if(lastInstancePath.isNil.or { (Date.getDate.daysDiff(lastInstanceDate)) > 1 }) {
            Document.open(newInstanceFunc.(scene));
          } {
            Document.open(lastInstancePath);
          }
        };

      };

      btn = styler.getSizableButton(subView, "open template", size: 90@lineheight);
      btn.action = {|btn| Document.open(templatepath) };
      btn = styler.getSizableButton(subView, "new instance", size: 90@lineheight);
      btn.action = {|btn| newInstanceFunc.(scene); instanceList.value_(0) };

      styler.getHorizontalSpacer(subView, width-20);
      styler.getSizableText(subView, "Load on selection", width-20);

      // Radiobuttons
      // No Auto Loading
      // Auto Load Latest Instance
      // Auto Load New Instance (if latest is older than x days)
      radio = RadioSetView(subView, (width)@lineheight).font_(subStyler.font).textWidth_(40).radioWidth_(lineheight).traceColor_(subStyler.stringColor).background_(subStyler.backgroundColor).textAlign_(\center);
      radio.add("none");
      radio.add("last inst");
      radio.add("new inst");
      radio.setSelected(onSelectOption);
      radio.action_({|vw,idx| onSelectOption = idx });


      // Instance List
      instanceList = ListView(subView, (width-20)@100)
      .items_(this.getInstanceNamesForScene(scene))
      .value_(nil).stringColor_(Color.white).background_(Color.clear)
      .hiliteColor_(Color.new(0.3765, 0.5922, 1.0000, 0.5)).font_(subStyler.font);

      // TODO: doubleClick to open instance
      instanceList.action_({|vw| "%/%_%.scd".format(instancePath, vw.items[vw.value], scene).postln });
      instanceList.enterKeyAction_({|vw|
        Document.open("%/%_%.scd".format(instancePath, vw.items[vw.value], scene))
      });
      // *** END SCENE INFO WINDOW ***

      loadInstanceFunc.(onSelectOption);

    }); // END SCENELIST ACTION

    ^win.alwaysOnTop_(true).front;
  }
}


