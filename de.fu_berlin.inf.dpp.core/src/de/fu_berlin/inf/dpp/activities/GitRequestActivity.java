package de.fu_berlin.inf.dpp.activities;

import com.thoughtworks.xstream.annotations.XStreamAlias;
import de.fu_berlin.inf.dpp.session.User;

@XStreamAlias("GitRequestActivity")
public class GitRequestActivity extends AbstractActivity {

  public GitRequestActivity(User source) {
    super(source);
  }

  @Override
  public void dispatch(IActivityReceiver receiver) {
    receiver.receive(this);
  }
}
