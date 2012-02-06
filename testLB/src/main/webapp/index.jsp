

<html>
<head><title>Session Content</title></head>
<body>

<a href="session"> Get session state</a>
<br/>
<br/>
<h2>Add to session</h2>
<form action="session" method="POST">
    
    Key: <input type="TEXT" name="key" />
    <br/>
    Value: <input type="TEXT" name="value" />
    <br/>
    <input type="SUBMIT" value="Add"/>
    <input type="HIDDEN" name="action" value="addToSession"/>
</form>

<h2>Remove from session</h2>
<form action="session" method="POST">
    
    Key: <input type="TEXT" name="key" />
    <input type="SUBMIT" value="Remove" />
    <input type="HIDDEN" name="action" value="removeFromSession" />
</form>

<h2>Invalidate session</h2>
<form action="session" method="POST">
    
    <input type="SUBMIT" value="Invalidate session" />
    <input type="HIDDEN" name="action" value="invalidateSession" />
</form>

<h2>Set max inactivity interval</h2>
<form action="session" method="POST">
    Time (in seconds): <input type="TEXT" name="mii" />
    <input type="SUBMIT" value="Set max inactivity interval" />
    <input type="HIDDEN" name="action" value="setSessionMII" />
</form>

<h2>Get session memory consumption</h2>
<form action="sessionCounter" method="GET">
    
    <input type="SUBMIT" value="Get session size"/>
</form>

<h2>Get (no session) attribute</h2>
<form action="session" method="POST">
    Key: <input type="TEXT" name="key" />
    <br/>
    <input type="HIDDEN" name="action" value="getCacheValue" />
    <input type="SUBMIT" value="Get attribute"/>
</form>

<h2>Set (no session) attribute</h2>
<form action="session" method="POST">
    Key: <input type="TEXT" name="key" />
    <br/>
    Value: <input type="TEXT" name="value" />
    <br/>

    <input type="HIDDEN" name="action" value="setCacheValueOK" />
    <input type="SUBMIT" value="set attribute"/>
</form>

<h2>Set (no session) attribute - throw exception after setting both session and app. cache values</h2>
<form action="session" method="POST">
    Key: <input type="TEXT" name="key" />
    <br/>
    Value: <input type="TEXT" name="value" />
    <br/>

    <input type="HIDDEN" name="action" value="setCacheValueERR" />
    <input type="SUBMIT" value="set attribute (expect exception)"/>
</form>

</body>
</html>


