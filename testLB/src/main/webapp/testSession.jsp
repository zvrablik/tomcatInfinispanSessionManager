
<html>
<head><title>Session Content</title></head>
<body>

<h2> test session operations serverlet </h2>

<a href="session"> Get session state</a>
<br/>
<br/>
<h2>Put to session</h2>
<form action='sessionTest' method="post">
    
    Key: <input type="TEXT" name="key" />
    <br/>
    Value: <input type="TEXT" name="value" />
    <br/>
    <input type="SUBMIT" value="Put" />
    <input type="HIDDEN" name="action" value="put" />
</form>

<h2>Get from session</h2>
<form action='sessionTest' method="get">
    
    Key: <input type="TEXT" name="key" />
    <input type="SUBMIT" value="Get" />
</form>

<h2>Remove from session</h2>
<form action='sessionTest' method="post">
    
    Key: <input type="TEXT" name="key" />
    <input type="SUBMIT" value="Remove" />
    <input type="HIDDEN" name="action" value="delete" />
</form>

</body>
</html>
