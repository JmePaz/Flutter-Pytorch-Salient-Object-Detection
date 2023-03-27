import 'dart:developer';
import 'dart:io';
import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:image_picker/image_picker.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  // This widget is the root of your application.
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Flutter Demo',
      theme: ThemeData(
        // This is the theme of your application.
        //
        // Try running your application with "flutter run". You'll see the
        // application has a blue toolbar. Then, without quitting the app, try
        // changing the primarySwatch below to Colors.green and then invoke
        // "hot reload" (press "r" in the console where you ran "flutter run",
        // or simply save your changes to "hot reload" in a Flutter IDE).
        // Notice that the counter didn't reset back to zero; the application
        // is not restarted.
        primarySwatch: Colors.blue,
      ),
      home: const MyHomePage(title: 'Image Background Remover'),
    );
  }
}

class MyHomePage extends StatefulWidget {
  const MyHomePage({super.key, required this.title});

  // This widget is the home page of your application. It is stateful, meaning
  // that it has a State object (defined below) that contains fields that affect
  // how it looks.

  // This class is the configuration for the state. It holds the values (in this
  // case the title) provided by the parent (in this case the App widget) and
  // used by the build method of the State. Fields in a Widget subclass are
  // always marked "final".

  final String title;

  @override
  State<MyHomePage> createState() => _MyHomePageState();
}

class _MyHomePageState extends State<MyHomePage> {
  static late MethodChannel channel;
  String status = "";
  late Image _img;
  late File imgFile;
  bool hasLoadedImg = false;
  bool isBusy = false;

  @override
  void initState() {
    // TODO: implement initState
    super.initState();
    channel = const MethodChannel("com.paz/imgProcessing");
    loadModel();
    //loadModel2();
  }

  //loading model
  Future<void> loadModel() async {
    setState(
      () {
        status = "loading model";
      },
    );
    String keyPath = "assets/model/u2netp.ptl";

    try {
      await channel.invokeMethod("loadModel", {"path": keyPath});
      setState(
        () {
          status = "Model loaded successfully...";
        },
      );
    } catch (e) {
      setState(
        () {
          status = "Error occurred in loading the model. $e";
        },
      );
    }
  }

  void _getImgOnGallery() async {
    final ImagePicker imgPicker = ImagePicker();
    final pickedFile = await imgPicker.pickImage(source: ImageSource.gallery);
    if (pickedFile == null) {
      return;
    }
    imgFile = File(pickedFile.path);

    setState(() {
      _img = Image.file(imgFile);
      hasLoadedImg = true;
    });
  }

  void segmentObjectOnImage() async {
    try {
      setState(() {
        isBusy = true;
      });

      Uint8List imgBytes = await channel
          .invokeMethod("segmentObjectOnImage", {"path": imgFile.path});
      setState(() {
        isBusy = false;
        _img = Image.memory(imgBytes);
      });
    } catch (e) {
      setState(
        () {
          status = "Error occurred in segmenting the object. $e";
        },
      );
    }
  }

  void segmentationCatchError() {
    PlatformDispatcher.instance.onError = (error, stack) {
      segmentObjectOnImage();
      return true;
    };
  }

  @override
  Widget build(BuildContext context) {
    // This method is rerun every time setState is called, for instance as done
    // by the _incrementCounter method above.
    //
    // The Flutter framework has been optimized to make rerunning build methods
    // fast, so that you can just rebuild anything that needs updating rather
    // than having to individually change instances of widgets.
    return Scaffold(
        appBar: AppBar(
          // Here we take the value from the MyHomePage object that was created by
          // the App.build method, and use it to set our appbar title.
          title: Text(widget.title),
        ),
        body: Center(
          // Center is a layout widget. It takes a single child and positions it
          // in the middle of the parent.
          child: Column(
            // Column is also a layout widget. It takes a list of children and
            // arranges them vertically. By default, it sizes itself to fit its
            // children horizontally, and tries to be as tall as its parent.
            //
            // Invoke "debug painting" (press "p" in the console, choose the
            // "Toggle Debug Paint" action from the Flutter Inspector in Android
            // Studio, or the "Toggle Debug Paint" command in Visual Studio Code)
            // to see the wireframe for each widget.
            //
            // Column has various properties to control how it sizes itself and
            // how it positions its children. Here we use mainAxisAlignment to
            // center the children vertically; the main axis here is the vertical
            // axis because Columns are vertical (the cross axis would be
            // horizontal).
            mainAxisAlignment: MainAxisAlignment.center,
            children: <Widget>[
              (isBusy)
                  ? const CircularProgressIndicator()
                  : (hasLoadedImg)
                      ? _img
                      : Container(),
              (hasLoadedImg)
                  ? ElevatedButton(
                      onPressed: segmentObjectOnImage,
                      child: const Text("Remove Background"))
                  : Container(),
              ElevatedButton(
                  onPressed: _getImgOnGallery,
                  child: const Text("Upload an Image"))
            ],
          ),
        ) // This trailing comma makes auto-formatting nicer for build methods.
        );
  }
}
