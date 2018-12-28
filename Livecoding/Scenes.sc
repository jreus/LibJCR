/******************************************
Livecoding scenography

(C) 2018 Jonathan Reus
GPL

*******************************************/



/*_____________________________________________________________
@class
Scenes
A scene system for livecoding. Template files are stored in a
"scenes" directory, and instances are created from those templates
that can be performed and modified at will without transforming the
original.

Works a bit like Macros but on the scale of sonic scenes.


@usage

f = "".resolveRelative +/+ "scenes";
z = Scenes(f).makeGui;
________________________________________________________________*/

Scenes {
	var scenePath, instancePath, sceneNames;
	var win;
	*new {|scenedir|
		^super.new.init(scenedir);
	}

	init {|scenedir|
		if(scenedir.isNil) { scenedir = Document.current.path.dirname +/+ "scenes/" };
		scenedir.postln;
		scenePath = scenedir;
		if(File.exists(scenePath).not) { File.mkdir(scenePath) };
		sceneNames = (scenePath +/+ "*.scd").pathMatch.collect {|it|
			PathName(it).fileNameWithoutExtension
		};
		instancePath = scenePath +/+ "instances/";
		if(File.exists(instancePath).not) { File.mkdir(instancePath) };
	}

	makeGui {
		var width = 200, height = 600, lineheight=20;
		var styler, decorator, childView;
		var sceneList, sceneName, addBtn, deleteBtn;
		var subView, subStyler;
		if(win.notNil) { win.close };
		win = Window("Scene Navigator", (width+10)@400);
		styler = GUIStyler(win);

		// child view inside window, this is where all the gui views sit
		childView = styler.getWindow("Scenes", win.view.bounds);
		childView.decorator = FlowLayout(childView.bounds, 5@5);

		sceneName = TextField.new(childView, width@lineheight);
		sceneName.action = {|field| addBtn.doAction };

		addBtn = styler.getSizableButton(childView, "+", "+", lineheight@lineheight);
		deleteBtn = styler.getSizableButton(childView, "-", "-", lineheight@lineheight);

		sceneList = ListView(childView, width@200)
		.items_(sceneNames.asArray).value_(nil)
		.stringColor_(Color.white).background_(Color.clear)
		.hiliteColor_(Color.new(0.3765, 0.5922, 1.0000, 0.5));

		addBtn.action = {|btn|
			var idx, newscene = sceneName.value;
			idx = sceneList.items.indexOfEqual(newscene);
			if(idx.notNil) { // a scene by that name already exists
				"A scene with the name % already exists".format(newscene).warn;
			} { // create a new scene
				var scenepath = scenePath +/+ newscene ++ ".scd";
				File.use(scenepath, "w", {|fp| fp.write("/* New Scene */") });
				sceneList.items = sceneList.items.add(newscene);
			};
		};

		deleteBtn.action = {|btn|
			var dialog,tmp,bounds,warning,scene,scenepath;
			scene = sceneList.items[sceneList.value];
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


		subStyler = GUIStyler(childView); // styler for subwindow
		subView = subStyler.getWindow("Subwindow", width@600); // subwindow

		sceneList.action_({ |lv| // action when selecting items in the scene list -> either open a new instance, or the current instance
			var btn;
			var matching, scene, scenepath;
			scene = lv.items[lv.value];
			scenepath = scenePath +/+ scene ++ ".scd";

			scene.postln;
			sceneName.string = scene;

			// Does an instance of the scene already exist?
			matching = Document.openDocuments.select {|doc| doc.title.contains(scene) };

			matching.postln;

			matching.size.switch(
				0, { // if no, create a new file/instance & open it
					var original, instance;
					instance = instancePath +/+ Date.getDate.stamp ++ "_" ++ scene ++ ".scd";
					File.use(instance, "w", {|fp| fp.write(File.readAllString(scenepath)) });
					Document.open(instance);
				},
				1, { // if yes, open that instance
					Document.open(matching[0].path);
				},
				{ // otherwise you have multiple open instances
					var im = matching.select {|doc| doc.title != (scene++".scd") };
					Document.open(im[0].path);
					"Multiple Matching Instances... ".postln;
				}
			);

			// Show options for selected scene
			subView.removeAll; // remove gui for previously selected scene
			subView.decorator = FlowLayout(subView.bounds);
			btn = styler.getSizableButton(subView, "source", size: 50@lineheight);
			btn.action = {|btn| Document.open(scenepath) };
		}); // END SCENELIST ACTION


		win.front;
	}
}


