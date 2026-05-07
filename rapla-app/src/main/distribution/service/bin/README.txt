-----------------------------------------------------------------------------
Java Service Wrapper Community Edition 3.5.54
Copyright (C) 1999-2023 Tanuki Software, Ltd. All Rights Reserved.
https://wrapper.tanukisoftware.com
-----------------------------------------------------------------------------

Depending on the security policy of your Windows installation, a popup window
titled "Windows protected your PC" may appear when trying to execute the batch
files located in the bin folder. The Wrapper binaries have been signed to
verify that they are unmodified as published by Tanuki Software. But batch
files by nature can't be signed in the same way.

The batch files are provided to ease launching the Wrapper binaries, and it is
perfectly safe to execute them if they were acquired from the official
links on the Tanuki Software Wrapper website or SourceForge.

Here is a simple workaround:

1. Right click on the bat file you want to execute and open the Properties
   window from the contextual menu.
2. At the bottom of the "General" tab, you should see a "Security" section
   with a "Unblock" checkbox or button. Click on the button or check the box.
3. Click OK.
4. You should now be able to execute the BAT file without warning.

It is also possible to do the same on the downloaded zip file before extracting
it. All of the extracted files will maintain the unblocked status.

For further explanation regarding this issue, please refer to the following
page of our website:
    https://wrapper.tanukisoftware.com/doc/english/howto-windows-install.html#zip
