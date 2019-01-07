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
	var rootPath, scenePath, instancePath, sceneNames;
	var win, onSelectOption=0;
	*new {|scenedir|
		^super.new.init(scenedir);
	}

  // should throw a fatal error if not being run from root.scd
	init {|rootpath, scenedir|
    if(rootpath.isNil) { // If rootpath is not specified then this method must be called from root.scd
      if(PathName(Document.current.path).fileName != "root.scd") {
        "Scenes.new must be called from 'root.scd', or a path to 'root.scd' must be provided".throw;
      };
      rootpath = Document.current.path;
    };
    rootPath = rootpath;
		if(scenedir.isNil) { scenedir = rootpath.dirname +/+ "_scenes/" };
		rootpath.postln;
    scenedir.postln;
		scenePath = scenedir;
		if(File.exists(scenePath).not) {
      File.mkdir(scenePath);
    };

    if(File.exists(rootPath).not) {
      File.use(rootPath,"w", {|fp|
        fp.write("/*** Root Scene ***/\nMacros.load\n~sc=Scene.new\n")
      });
    };
		sceneNames = (scenePath +/+ "*.scd").pathMatch.collect {|it|
			PathName(it).fileNameWithoutExtension
		};
    "SCENE NAMES % %".format(sceneNames[0].class.postln, sceneNames).postln;
    sceneNames = sceneNames.insert(0, "root"); // root is a reserved scene name
    "insert worked..".warn;
		instancePath = scenePath +/+ "instances/";
		if(File.exists(instancePath).not) { File.mkdir(instancePath) };
	}

	makeGui {|position|
		var width = 200, height = 600, lineheight=20, top=0, left=0;
		var styler, decorator, childView;
		var sceneList, sceneName, addBtn, deleteBtn;
		var subView, subStyler;
		if(win.notNil) { win.close };
    if(position.notNil) {
      top = position.y; left = position.x;
    };
    win = Window("Scene Navigator", Rect(left, top, (width+10), height));
		styler = GUIStyler(win);

		// child view inside window, this is where all the gui views sit
		childView = styler.getWindow("Scenes", win.view.bounds);
		childView.decorator = FlowLayout(childView.bounds, 5@5);

		sceneName = TextField.new(childView, width@lineheight);
		sceneName.action = {|field| addBtn.doAction };

		addBtn = styler.getSizableButton(childView, "+", "+", lineheight@lineheight);
		deleteBtn = styler.getSizableButton(childView, "-", "-", lineheight@lineheight);

    sceneList = ListView(childView, width@(height/4))
		.items_(sceneNames.asArray).value_(nil)
		.stringColor_(Color.white).background_(Color.clear)
		.hiliteColor_(Color.new(0.3765, 0.5922, 1.0000, 0.5));

		addBtn.action = {|btn|
			var idx, newscene = sceneName.value;
      if(newscene == "root") {
        "'root' is a reserved scene name".error;

      } {
			idx = sceneList.items.indexOfEqual(newscene);
			if(idx.notNil) { // a scene by that name already exists
				"A scene with the name % already exists".format(newscene).warn;
			} { // create a new scene
				var scenepath = scenePath +/+ newscene ++ ".scd";
        "new file at % % %".format(scenepath, newscene, sceneName.value).postln;
				File.use(scenepath, "w", {|fp| fp.write("/* New Scene % */".format(newscene)) });
				sceneList.items = sceneList.items.add(newscene);
			};
      };
		};

		deleteBtn.action = {|btn|
			var dialog,tmp,bounds,warning,scene,scenepath;
			scene = sceneList.items[sceneList.value];
      if(scene == "root") {
        "'root' cannot be deleted".error;

      } {
			scenepath = scenePath +/+ scene ++ ".scd";
			warning = "Delete %\nAre you sure?".format(scene);
			bounds = Rect(win.bounds.left, win.bounds.top + 25, win.bounds.width, win.bounds.height);
			dialog = Window.new("Confirm", bounds, false, false);
			dialog.alwaysOnTop_(true);
			dialog.view.decorator = FlowLayout.new(dialog.view.bounds);
			StaticText.new(dialog, 200@40).string_(warning);
			Button.new(dialog, 60@30).string_("Yes").action_({|btn|
				var newitems;
				newitems = sceneList.items.copy; newitems.removeAt(sceneList.value);
				sceneList.items = newitems;
				File.delete(scenepath);
				"Delete %".format(sceneList.items[sceneList.value]).postln;
				dialog.close;
			});
			Button.new(dialog, 60@30).string_("Cancel").action_({|btn|
				"Abort".postln;
				dialog.close;
			});
      dialog.front;
      };
		};


		subStyler = GUIStyler(childView); // styler for subwindow
    subView = subStyler.getWindow("Subwindow", width@(height/2)); // subwindow

		sceneList.action_({ |lv| // action when selecting items in the scene list -> build the scene info view
			var btn, radio, instanceList, loadInstanceFunc, newInstanceFunc, getInstanceNames;
			var matching, scene, templatepath;
			scene = lv.items[lv.value];
      if(scene == "root") {
        templatepath = rootPath;
      } {
        templatepath = scenePath +/+ scene ++ ".scd";
      };
			scene.postln;

      // *** BUILD SCENE INFO WINDOW ***
      subView.removeAll; // remove views & layout for previous scene info window
			subView.decorator = FlowLayout(subView.bounds);

      getInstanceNames = {|sceneName|
        (instancePath +/+ "*%.scd".format(sceneName)).pathMatch.sort.reverse.collect {|path|
          PathName(path).fileName[..12];
        };
      };

      newInstanceFunc = {|sceneName|
        var res, instances;
        res = instancePath +/+ Date.getDate.stamp ++ "_" ++ sceneName ++ ".scd";
        File.use(res, "w", {|fp| fp.write(File.readAllString(scenePath +/+ sceneName ++ ".scd")) });
        instances = getInstanceNames.(sceneName);
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

      // Radiobuttons
      // No Auto Loading
      // Auto Load Latest Instance
      // Auto Load New Instance (if latest is older than x days)
      radio = RadioSetView(subView, width@50).font_(subStyler.font).textWidth_(100).radioWidth_(10);
      radio.add("no load");
      radio.add("last instance");
      radio.add("new instance");
      radio.setSelected(onSelectOption);
      radio.action_({|vw,idx| onSelectOption = idx });


      // Instance List
      instanceList = ListView(subView, width@100)
      .items_(getInstanceNames.(scene))
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


