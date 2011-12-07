

<html>
<head><title>Session Content</title></head>
<body>

<a href="session"> Get session state</a>
<br/>
<br/>
<h2>Add to session</h2>
<form action='session' method="POST">
    
    Key: <input type="TEXT" name="key" />
    <br/>
    Value: <input type="TEXT" name="value" />
    <br/>
    <input type="SUBMIT" value="Add"/>
    <input type="HIDDEN" name="action" value="addToSession"/>
</form>

<h2>Remove from session</h2>
<form action='session' method="POST">
    
    Key: <input type="TEXT" name="key" />
    <input type="SUBMIT" value="Remove" />
    <input type="HIDDEN" name="action" value="removeFromSession" />
</form>

<h2>Invalidate session</h2>
<form action='session' method="POST">
    
    <input type="SUBMIT" value="Invalidate session" />
    <input type="HIDDEN" name="action" value="invalidateSession" />
</form>

<h2>Get session size</h2>
<form action='sessionCounter' method="GET">
    
    <input type="SUBMIT" value="Get session size"/>
</form>

</body>
</html>


