

<html>
<head><title>Session Content</title></head>
<body>

<a href="session"> Get session state</a>
<br/>
<br/>
<h1>Add to session</h1>
<form action='session' method="POST">
    
    Key: <input type="TEXT" name="key">
    <br>
    Value: <input type="TEXT" name="value" >
    <br>
    <input type="SUBMIT" value="Add">
    <input type="HIDDEN" name="action" value="addToSession">
</form>

<h1>Remove from session</h1>
<form action='session' method="POST">
    
    Key: <input type="TEXT" name="key">
    <input type="SUBMIT" value="Remove">
    <input type="HIDDEN" name="action" value="removeFromSession">
</form>

<h1>Invalidate session</h1>
<form action='session' method="POST">
    
    <input type="SUBMIT" value="Invalidate session">
    <input type="HIDDEN" name="action" value="invalidateSession">
</form>

</body>
</html>


