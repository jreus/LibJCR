/******************************************
Project for fast LiveCoding Language...

(C) 2019 Jonathan Reus

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

https://www.gnu.org/licenses/

_____________________________________________________________
Hexaplex

A lightweight livecoding language.

@usage
Xex.start(sampleBanks: []);

________________________________________________________________*/

Xex : Project {
  classvar <>lc_macropath;
  classvar <>sampleBankDict;

  *start {|server=nil, showMeters=true, sampleBanks=nil, limitSamplesGlobal=1000, macropath=nil, verbose=false, onBoot=nil|
    var sampleDirs;
    if(macropath.isNil) {
      if(lc_macropath.isNil) {
      lc_macropath = "/Users/jon/Drive/DEV/SC_LiveCoding/";
      };
    } {
      lc_macropath = macropath;
    };
    Macros.load(lc_macropath);

    if(sampleBankDict.isNil) { sampleBankDict = Dictionary.new };
    if(sampleBanks.isNil) {
      sampleBanks = sampleBankDict.keys.asArray;
    };

    sampleDirs = sampleBanks.collect {|bank|
        var dir = sampleBankDict.at(bank);
        if(dir.isNil) {
          "Hexaplex: Sample bank % does not exist".throw;
        };
        dir;
     };

    Xex.pr_makeStartupWindow(server, sampleDirs, onBoot, showMeters, verbose);
  }

}